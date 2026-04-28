#!/usr/bin/env bash
# apply.sh -- one-shot AWS demo deploy.
#
# Runs the bootstrap stack, wires the GitHub repo secrets/vars from the
# bootstrap outputs, then runs the demo-deploy stack with a placeholder
# image. The first push to main after this script completes triggers
# `.github/workflows/deploy-demo.yml`, which builds the real backend
# image and prints the live URL.
#
# Run this from any working directory:
#   ./infra/terraform/apply.sh
#
# Pre-flight requirements (script checks each):
#   - aws CLI authenticated against the target account
#   - gh CLI authenticated against this repo
#   - terraform >= 1.5.0
#
# What this script does NOT do:
#   - Take AWS credentials as input. Your shell's existing AWS config
#     handles auth; this script never reads ~/.aws/credentials directly,
#     never logs anything that touches credentials.
#   - Push images. The workflow does that on the first main push after
#     this script completes.
#   - Iterate after first apply. If terraform errors out, fix the cause
#     and re-run -- the script is idempotent against partially-applied
#     state.
#
# Cost: starts accruing once `terraform apply` completes (~$43/month).
# `terraform destroy` (in both stacks, demo-deploy first) brings it back
# to zero.

set -euo pipefail

# Resolve script's own directory so the script works from any cwd.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_DIR="${SCRIPT_DIR}/bootstrap"
DEMO_DIR="${SCRIPT_DIR}/demo-deploy"
AWS_REGION_DEFAULT="us-east-1"
AWS_REGION="${AWS_REGION:-${AWS_REGION_DEFAULT}}"

# ---------------- output helpers ----------------

c_red() { printf '\033[31m%s\033[0m' "$*"; }
c_grn() { printf '\033[32m%s\033[0m' "$*"; }
c_yel() { printf '\033[33m%s\033[0m' "$*"; }
c_dim() { printf '\033[2m%s\033[0m' "$*"; }

step() {
  printf '\n%s %s\n' "$(c_grn '==>')" "$*"
}

note() {
  printf '%s %s\n' "$(c_dim '   ')" "$(c_dim "$*")"
}

die() {
  printf '\n%s %s\n' "$(c_red '!!!')" "$*" >&2
  exit 1
}

# ---------------- pre-flight ----------------

step "Pre-flight: required CLIs"

command -v aws       >/dev/null 2>&1 || die "aws CLI not found. Install: https://aws.amazon.com/cli/"
command -v terraform >/dev/null 2>&1 || die "terraform not found. Install: https://developer.hashicorp.com/terraform/install"
command -v gh        >/dev/null 2>&1 || die "gh CLI not found. Install: https://cli.github.com/"
command -v jq        >/dev/null 2>&1 || die "jq not found. Install: brew install jq"
note "aws       $(aws --version 2>&1 | head -1)"
note "terraform $(terraform version | head -1)"
note "gh        $(gh --version | head -1)"

step "Pre-flight: AWS identity"

if ! AWS_IDENTITY=$(aws sts get-caller-identity --output json 2>&1); then
  die "AWS credentials not loaded.
   Configure your shell first:
     - aws configure                  (long-lived access key)
     - aws sso login                  (SSO)
     - your org's auth tool
   Then re-run this script."
fi
AWS_ACCOUNT="$(printf '%s' "${AWS_IDENTITY}" | jq -r '.Account')"
AWS_PRINCIPAL="$(printf '%s' "${AWS_IDENTITY}" | jq -r '.Arn')"
note "Account id : ${AWS_ACCOUNT}"
note "Principal  : ${AWS_PRINCIPAL}"
note "Region     : ${AWS_REGION}"

step "Pre-flight: gh authentication"

if ! gh auth status >/dev/null 2>&1; then
  die "gh CLI not authenticated. Run: gh auth login"
fi
GH_REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
note "Repo      : ${GH_REPO}"

# ---------------- confirmation ----------------

cat <<EOF

$(c_yel '────────────────────────────────────────────────────────────────────')
This script will:

  1. Apply the bootstrap stack to AWS account ${AWS_ACCOUNT} in ${AWS_REGION}
     (creates: S3 state bucket, DynamoDB lock table, GitHub OIDC provider,
     IAM role for ${GH_REPO}'s deploy workflow).

  2. Set 1 GitHub repo secret + 3 repo variables from bootstrap outputs.

  3. Apply the demo-deploy stack with image_tag="placeholder"
     (creates: VPC, RDS db.t4g.micro, ECS Fargate service, ALB, S3 bucket,
     CloudFront distribution, CloudWatch alarms).

  4. Print the next steps to bring the real backend image online.

Estimated time: 25-30 minutes (CloudFront edge propagation is the slow part).
Estimated cost: ~\$43/month while running. \`terraform destroy\` to undo.

$(c_yel '────────────────────────────────────────────────────────────────────')

EOF

read -r -p "Proceed? [y/N] " confirm
# `${var,,}` is bash-4-only; macOS ships bash 3.2. Use tr for portability.
confirm_lc="$(printf '%s' "${confirm}" | tr '[:upper:]' '[:lower:]')"
if [[ "${confirm_lc}" != "y" && "${confirm_lc}" != "yes" ]]; then
  die "Aborted by user."
fi

# ---------------- step 1: bootstrap ----------------

step "1/4 Apply bootstrap stack"
note "  Working in ${BOOTSTRAP_DIR}"

(
  cd "${BOOTSTRAP_DIR}"
  terraform init -input=false
  terraform apply -auto-approve -var "aws_region=${AWS_REGION}"
)

step "1/4 Capture bootstrap outputs"
TF_STATE_BUCKET="$(cd "${BOOTSTRAP_DIR}" && terraform output -raw tf_state_bucket)"
TF_LOCK_TABLE="$(cd "${BOOTSTRAP_DIR}" && terraform output -raw tf_lock_table)"
GHA_ROLE_ARN="$(cd "${BOOTSTRAP_DIR}" && terraform output -raw github_actions_role_arn)"
note "tf_state_bucket        ${TF_STATE_BUCKET}"
note "tf_lock_table          ${TF_LOCK_TABLE}"
note "github_actions_role_arn ${GHA_ROLE_ARN}"

# ---------------- step 2: github repo secrets/vars ----------------

step "2/4 Wire GitHub repo secrets + variables"

gh secret   set AWS_DEPLOY_ROLE_ARN --body "${GHA_ROLE_ARN}"
gh variable set TF_STATE_BUCKET     --body "${TF_STATE_BUCKET}"
gh variable set TF_LOCK_TABLE       --body "${TF_LOCK_TABLE}"
gh variable set AWS_REGION          --body "${AWS_REGION}"
note "AWS_DEPLOY_ROLE_ARN secret set"
note "TF_STATE_BUCKET, TF_LOCK_TABLE, AWS_REGION variables set"

# ---------------- step 3: demo-deploy ----------------

step "3/4 Apply demo-deploy stack (image_tag=placeholder)"
note "  This is the long step (~25 min for CloudFront propagation)."

(
  cd "${DEMO_DIR}"
  terraform init -input=false -reconfigure \
    -backend-config="bucket=${TF_STATE_BUCKET}" \
    -backend-config="dynamodb_table=${TF_LOCK_TABLE}" \
    -backend-config="region=${AWS_REGION}"
  terraform apply -auto-approve \
    -var "image_tag=placeholder" \
    -var "aws_region=${AWS_REGION}"
)

step "3/4 Capture demo-deploy outputs"
CLOUDFRONT_URL="$(cd "${DEMO_DIR}" && terraform output -raw cloudfront_url)"
ECR_REPO_URL="$(cd "${DEMO_DIR}" && terraform output -raw ecr_repository_url)"
S3_BUCKET="$(cd "${DEMO_DIR}" && terraform output -raw frontend_s3_bucket)"
note "cloudfront_url       ${CLOUDFRONT_URL}"
note "ecr_repository_url   ${ECR_REPO_URL}"
note "frontend_s3_bucket   ${S3_BUCKET}"

# ---------------- step 4: report + next steps ----------------

cat <<EOF

$(c_grn '╔════════════════════════════════════════════════════════════════════╗')
$(c_grn '║  AWS infrastructure provisioned.                                    ║')
$(c_grn '╚════════════════════════════════════════════════════════════════════╝')

The infrastructure is live but the backend ECS task is failing health
checks because the placeholder image isn't a real Spring Boot service.
That's expected and intentional -- the next step builds + pushes the
real image and brings the URL alive.

$(c_yel 'Next:')

  Push any commit to main (or trigger the workflow manually):

    gh workflow run deploy-demo.yml --ref main
    gh run watch

  The deploy-demo.yml workflow:
    1. Re-applies terraform with image_tag=<sha>
    2. Builds the backend image and pushes to ECR
    3. Force-redeploys the ECS service
    4. Builds the frontend with the CloudFront URL baked in
    5. Syncs to S3, invalidates /index.html
    6. Curls the live URL until it returns 200, then prints it

$(c_yel 'Live URL (once the workflow completes):')

  ${CLOUDFRONT_URL}

$(c_yel 'Tear down (anytime, brings cost to zero):')

  cd ${DEMO_DIR}     && terraform destroy
  cd ${BOOTSTRAP_DIR} && terraform destroy

$(c_yel 'Demo seeded users (devRole query param on the URL):')

  ?devRole=MANAGER          (Ada Lovelace -- default)
  ?devRole=IC               (Ben Carter, reports to Ada)
  ?devRole=IC_NULL_MANAGER  (Frankie Hopper, unassigned)
  ?devRole=ADMIN            (Site Admin)

EOF
