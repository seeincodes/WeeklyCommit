# Demo deploy — AWS infrastructure (deferred)

This directory is the **placeholder** for the AWS Terraform that would lift
`docker-compose.demo.yml` onto AWS. **It is intentionally empty in this commit.**
The local docker-compose flow (see [../../../docs/runbook.md](../../../docs/runbook.md))
is the actively-supported demo path; the AWS lift is a follow-up task.

## Why deferred

The α-local scope agreed for the [`task/14-aws-demo-deploy`](https://github.com/seeincodes/WeeklyCommit/tree/task/14-aws-demo-deploy)
branch deliberately *did not* include the AWS Terraform. Reasons:

1. **First-apply needs human-driven AWS credentials.** The CI pipeline can run
   subsequent applies, but the bootstrap (S3 state bucket, IAM OIDC trust policy,
   ACM cert, Route53 zone if any) has to happen from a privileged session. That's
   you, not the agent.
2. **The local docker-compose stack reproduces the exact same backend +
   frontend container images** that the AWS deploy will run. Validating the
   demo locally first means the cloud apply only debugs *infrastructure* issues,
   not application issues.
3. **Scope discipline.** "Get a working demo I can click" is achievable now;
   "AWS-hosted demo with a public URL" requires another full branch's worth of
   Terraform (~600 LOC), GHA wiring, and post-apply iteration. Splitting them
   keeps the feedback loop tight.

## What the AWS lift will need

When you're ready to take the docker-compose stack to AWS, the follow-up
branch (`task/14-aws-deploy-cloud`) should land:

### Terraform modules

| Resource                              | Purpose                                              | Estimated cost ($/mo) |
|---------------------------------------|------------------------------------------------------|----------------------:|
| VPC + 2 public subnets + IGW          | Network for the ECS task and ALB                     | $0                    |
| ECR repository (one)                  | Backend image registry                               | ~$0 (well under 0.5GB) |
| RDS Postgres 16.4 db.t4g.micro single-AZ | Demo DB; deviation from PRD's Multi-AZ           | ~$13                  |
| ECS Fargate cluster + service (1 task)| Backend runtime                                      | ~$15 (0.25 vCPU + 0.5 GB RAM) |
| Application Load Balancer             | TLS termination + health checks for ECS              | ~$18                  |
| S3 bucket                             | Frontend static origin                               | ~$0.50                |
| CloudFront distribution               | TLS in front of S3 + ALB; routes `/api/*` to ALB     | ~$1                   |
| Secrets Manager (1 secret)            | RDS password                                         | ~$0.40                |
| IAM roles (task execution, task, OIDC)| Permissions                                          | $0                    |
| CloudWatch log groups                 | ECS task stdout                                      | ~$0.10                |
| **Total**                             |                                                      | **~$48/mo**           |

### File layout (proposed)

```
infra/terraform/demo-deploy/
  versions.tf         # AWS provider 5.x, terraform 1.7+
  variables.tf        # region, environment, image_tag
  vpc.tf              # public-only VPC, 2 AZs
  ecr.tf              # one repo, lifecycle rule keeps last 10 images
  rds.tf              # db.t4g.micro, single-AZ, gp3 20GB, retention 7d
  secrets.tf          # one Secrets Manager entry for DB password
  ecs.tf              # cluster, task definition, service
  alb.tf              # ALB, target group, listener, security groups
  s3.tf               # frontend bucket + bucket policy for CloudFront OAC
  cloudfront.tf       # distribution with two behaviors: default→S3, /api/*→ALB
  iam.tf              # task execution role, task role, GitHub OIDC role
  outputs.tf          # the live URL
  README.md           # apply instructions
```

### CI deploy workflow

`.github/workflows/deploy-demo.yml` should:

1. Build the backend image with the layered-jar Dockerfile
2. Push to ECR with the commit SHA as the tag
3. Update the ECS task definition to point at the new image tag
4. Force-new-deployment on the service
5. Build the frontend with `VITE_API_BASE_URL=https://${cloudfront_domain}` and
   `VITE_DEMO_MODE=true`
6. Sync `dist/` to the S3 bucket
7. Invalidate the CloudFront cache for `/index.html` (bundled assets are
   content-hashed and never need invalidation)

### Sequencing

Suggest this order, each a separate commit so a stuck step is easy to roll back:

1. `task(14): bootstrap S3 state bucket + DynamoDB lock + OIDC role`
2. `task(14): VPC + ECR + RDS + Secrets Manager`
3. `task(14): ALB + ECS + first manual deploy`
4. `task(14): S3 + CloudFront + frontend deploy`
5. `task(14): GHA deploy-demo workflow + post-merge automation`

Each step is a `terraform apply` you run from your AWS-credentialed shell. The
agent can write the HCL but cannot run the apply.

## Today, this directory holds

Nothing. Run `docker compose -f docker-compose.demo.yml up --build` from the
repo root and the demo runs at <http://localhost:5173>.
