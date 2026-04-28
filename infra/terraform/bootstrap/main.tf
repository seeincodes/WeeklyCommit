################################################################################
# Bootstrap stack -- runs ONCE, manually, with the user's AWS credentials.
#
# Provisions the prerequisites that the demo-deploy stack assumes already exist:
#
#   1. S3 bucket for Terraform remote state (versioned, encrypted, locked down).
#   2. DynamoDB table for state locking.
#   3. GitHub OIDC provider + IAM role that .github/workflows/deploy-demo.yml
#      assumes when running from CI.
#
# Why split this off from demo-deploy: chicken-and-egg. The demo-deploy stack's
# `backend "s3"` block references the bucket THIS stack creates. If they were
# the same root module you'd have to apply with a local backend first, then
# migrate state -- error-prone. Keeping them separate makes the apply order
# linear: bootstrap once, then demo-deploy any number of times.
#
# State for THIS stack lives locally. The state file is small, immutable after
# the one-time apply, and the human running the apply commits it nowhere
# (terraform.tfstate goes in the .gitignore for this dir). Loss-of-state
# consequence: you'd just re-import the resources, since they're tagged.
################################################################################

data "aws_caller_identity" "current" {}

# ---------------- S3 state bucket ----------------

resource "aws_s3_bucket" "tf_state" {
  bucket = "weekly-commit-tfstate-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket                  = aws_s3_bucket.tf_state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Lifecycle: keep non-current versions for 90 days so a state corruption can be
# rolled back, but don't accumulate forever. Terraform state is small enough
# that 90 days of versions is negligible storage cost.
resource "aws_s3_bucket_lifecycle_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# ---------------- DynamoDB state lock ----------------

resource "aws_dynamodb_table" "tf_lock" {
  name         = "weekly-commit-tflock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

# ---------------- GitHub OIDC provider ----------------

# IAM OIDC provider for GitHub Actions. Single provider per AWS account; if one
# already exists in the account from another project, import it via:
#   terraform import aws_iam_openid_connect_provider.github
#     arn:aws:iam::<acct>:oidc-provider/token.actions.githubusercontent.com
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# ---------------- GitHub Actions deploy role ----------------

variable "github_repository" {
  type        = string
  description = "GitHub repo in `owner/name` form. The OIDC trust policy restricts assumption to this repo's main-branch workflows."
  default     = "seeincodes/WeeklyCommit"
}

data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Restrict to the configured repo + main branch + PRs against main.
    # The PR pattern lets a PR's deploy-on-preview job run; main pattern lets
    # post-merge deploys run. Without this scoping, any GitHub Action in any
    # repo could assume this role.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository}:ref:refs/heads/main",
        "repo:${var.github_repository}:pull_request",
      ]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name               = "weekly-commit-gha-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
  description        = "GitHub Actions assumes this role to run deploy-demo.yml. Trust policy restricts to ${var.github_repository}."
}

# Permissions broad enough to run the demo-deploy stack end-to-end. NOT a
# least-privilege policy -- a follow-up commit can scope this down to specific
# resource ARNs once the demo-deploy stack has applied at least once and the
# resource ARNs are stable. For a demo, "all my demo resources" is acceptable;
# for production this should be replaced with a CDK / Terraform-emitted
# narrow policy.
data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid    = "TerraformPlanApply"
    effect = "Allow"
    actions = [
      "ec2:*",
      "ecs:*",
      "ecr:*",
      "rds:*",
      "elasticloadbalancing:*",
      "iam:GetRole",
      "iam:PassRole",
      "iam:GetPolicy",
      "iam:GetPolicyVersion",
      "iam:ListAttachedRolePolicies",
      "iam:ListRolePolicies",
      "iam:GetRolePolicy",
      "logs:*",
      "s3:*",
      "cloudfront:*",
      "secretsmanager:*",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:DeleteItem",
      "kms:Decrypt",
      "kms:DescribeKey",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  role   = aws_iam_role.github_actions_deploy.id
  name   = "deploy-permissions"
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

# ---------------- Outputs ----------------

output "tf_state_bucket" {
  value       = aws_s3_bucket.tf_state.id
  description = "Pass this to the demo-deploy `backend.tf` and the GHA deploy workflow."
}

output "tf_lock_table" {
  value       = aws_dynamodb_table.tf_lock.name
  description = "Pass this to the demo-deploy `backend.tf`."
}

output "github_actions_role_arn" {
  value       = aws_iam_role.github_actions_deploy.arn
  description = "Set this as `AWS_DEPLOY_ROLE_ARN` in GitHub repo secrets so deploy-demo.yml can assume it."
}

output "aws_account_id" {
  value       = data.aws_caller_identity.current.account_id
  description = "Used by the deploy workflow to compute the ECR repo URI."
}
