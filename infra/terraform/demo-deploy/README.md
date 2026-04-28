# demo-deploy

Terraform stack that lifts the docker-compose demo onto AWS:

- **VPC** (public-only, 2 AZs, no NAT)
- **ECR** for the backend image
- **RDS** Postgres 16.4 db.t4g.micro single-AZ
- **ECS Fargate** task running the backend (256 cpu / 512 mem)
- **ALB** in front of the task (HTTP-only)
- **S3** for the frontend bundle
- **CloudFront** in front of S3 + ALB; routes `/api/*` and `/actuator/*` to the ALB, everything else to S3
- **CloudWatch alarms** on RDS CPU + ECS task count
- **Secrets Manager** for the RDS master password

## Cost (rough)

| Resource                                | $/month |
|-----------------------------------------|--------:|
| ECS Fargate task (256 cpu / 512 mem 24x7) | ~$10    |
| RDS db.t4g.micro single-AZ + 20GB gp3   | ~$13    |
| ALB                                     | ~$18    |
| CloudFront (PriceClass_100, low traffic)| ~$1     |
| S3 (frontend bundle, < 100 MB)          | ~$0.10  |
| Secrets Manager (1 secret)              | ~$0.40  |
| CloudWatch logs (low volume)            | ~$0.50  |
| ECR (last 10 images)                    | ~$0.10  |
| **Total**                               | **~$43**|

## Apply (first time)

Run from your AWS-credentialed shell. The first apply needs roughly 25 minutes
because CloudFront distribution creation is slow; subsequent applies take a
few minutes.

### 1. Run the bootstrap stack first

See [`../bootstrap/README.md`](../bootstrap/README.md). The bootstrap creates:

- The S3 bucket this stack uses for remote state
- The DynamoDB table for state locking
- The IAM role the GitHub Actions deploy workflow assumes

After bootstrap, you have four output values you need to keep handy:

```bash
cd ../bootstrap
terraform output
```

### 2. Set GitHub repo secrets + variables

```bash
gh secret set AWS_DEPLOY_ROLE_ARN --body "$(cd ../bootstrap && terraform output -raw github_actions_role_arn)"
gh variable set TF_STATE_BUCKET --body "$(cd ../bootstrap && terraform output -raw tf_state_bucket)"
gh variable set TF_LOCK_TABLE --body "$(cd ../bootstrap && terraform output -raw tf_lock_table)"
gh variable set AWS_REGION --body "us-east-1"
```

### 3. First apply (manual)

The first apply has a chicken-and-egg with ECR: the ECS task definition
references `<ecr-uri>:<sha>`, but ECR is empty. Two workarounds; pick one.

**Option A — apply with a placeholder image, push real one immediately after.**
Recommended; lets the deploy workflow's image push fix the state on the next push.

```bash
cd infra/terraform/demo-deploy
terraform init \
  -backend-config="bucket=$(cd ../bootstrap && terraform output -raw tf_state_bucket)" \
  -backend-config="dynamodb_table=$(cd ../bootstrap && terraform output -raw tf_lock_table)" \
  -backend-config="region=us-east-1"
terraform apply -var "image_tag=placeholder"
# ECS service will be in steady state with a failing task; that's expected.
# Push your first commit to main and the deploy-demo.yml workflow takes over.
```

**Option B — push an image first, then apply.** Slightly more involved but
ECS comes up healthy on the first apply.

```bash
# Build + push manually (one time):
ECR_URL=$(aws ecr describe-repositories --repository-names weekly-commit-service --query 'repositories[0].repositoryUri' --output text)
aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_URL"
# But wait -- ECR repo doesn't exist yet because demo-deploy hasn't been
# applied. Apply ECR-only first:
terraform apply -target=aws_ecr_repository.backend -var "image_tag=placeholder"
# Now build + push:
docker build -f apps/weekly-commit-service/Dockerfile -t "$ECR_URL:initial" .
docker push "$ECR_URL:initial"
# Now full apply:
terraform apply -var "image_tag=initial"
```

### 4. Verify

```bash
terraform output cloudfront_url
# Open in a browser. CloudFront edge propagation takes a few minutes for
# brand-new distributions; the deploy workflow's smoke check polls for up
# to 5 minutes.
```

## Subsequent deploys

After first apply, the GitHub Actions deploy workflow handles everything:
push to main → terraform apply → image build + push → ECS rolling deploy →
frontend build + S3 sync → CloudFront invalidation → smoke check.

`workflow_dispatch` is also enabled with a `skip_terraform: true` input, useful
if you only want to push fresh images without re-checking infra state.

## Destroy

```bash
cd infra/terraform/demo-deploy
terraform destroy
cd ../bootstrap
terraform destroy
```

If `force_destroy` on the S3 bucket fails (rare), empty the bucket manually
first via `aws s3 rm s3://<bucket> --recursive`.

## Production target

This stack is **not** the PRD-aligned production target. Documented deviations
([MEMO #11](../../../docs/MEMO.md#11-demo-deploy-ships-a-docker-compose-stack-first-aws-cloud-lift-is-a-follow-up-2026-04-27)):

| Aspect            | Demo                       | Production target                |
|-------------------|----------------------------|----------------------------------|
| Compute           | ECS Fargate single task    | EKS + HPA, min 2 / max 6 tasks   |
| Database          | RDS single-AZ              | RDS Multi-AZ                     |
| Network           | Public subnets, no NAT     | Private subnets + NAT or VPCe    |
| TLS at ALB        | HTTP-only (CloudFront fronts it) | HTTPS w/ ACM cert        |
| Auth              | Self-signed JWT in browser | Real Auth0 tenant                |
| Host integration  | Standalone SPA bundle      | Federated remote in PA host     |
| Notification svc  | Logging-only               | Real notification-svc           |
| RCDO upstream     | In-process stub            | Real RCDO host                   |
| Domain            | Default *.cloudfront.net   | weekly-commit.<org>.com          |
| GitOps            | GitHub Actions             | ArgoCD                           |
| ECR images        | Plain                      | Image scan + signed tags         |
| Audit retention   | None at infra level        | Backup retention 7d → S3 archive |

Each row is a deliberate cost / scope reduction. The follow-up branch lifts
each individually.
