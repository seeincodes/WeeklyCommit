# Bootstrap — run once, by hand

This stack provisions the prerequisites the `demo-deploy` stack assumes
already exist:

1. S3 bucket for Terraform remote state (versioned, encrypted, locked down)
2. DynamoDB table for state locking
3. GitHub OIDC provider + IAM role that the `deploy-demo.yml` workflow assumes

State for **this** stack is local and never committed. The state file is small,
applied once, and the resources are tagged so they can be re-imported if lost.

## Apply (first time)

```bash
cd infra/terraform/bootstrap

# Use whichever AWS credential mechanism you have set up. Profile, sso, env
# vars -- all fine. Confirm `aws sts get-caller-identity` returns the account
# you expect to deploy into.
aws sts get-caller-identity

terraform init
terraform plan
terraform apply
```

`apply` prints four outputs. Save them:

| Output                    | What to do with it                                                              |
|---------------------------|---------------------------------------------------------------------------------|
| `tf_state_bucket`         | Paste into `infra/terraform/demo-deploy/backend.tf` (line is commented)         |
| `tf_lock_table`           | Same — paste into `backend.tf`                                                  |
| `github_actions_role_arn` | Add to GitHub repo secrets as `AWS_DEPLOY_ROLE_ARN`                              |
| `aws_account_id`          | Add to GitHub repo secrets as `AWS_ACCOUNT_ID` (used to compute ECR repo URI)    |

Set the GitHub secrets via:

```bash
gh secret set AWS_DEPLOY_ROLE_ARN --body "$(terraform output -raw github_actions_role_arn)"
gh secret set AWS_ACCOUNT_ID --body "$(terraform output -raw aws_account_id)"
```

## When to re-apply

Almost never. The bootstrap is intentionally minimal so it doesn't drift.
Re-apply only if:

- You're rotating the OIDC trust thumbprint (rare — GitHub's cert)
- You're changing `var.github_repository` (forking, repo rename)
- You're adding additional GitHub Actions permissions to the deploy role

## When to destroy

Only if you're decommissioning the entire AWS demo. Order of operations:

1. `cd ../demo-deploy && terraform destroy`
2. `cd ../bootstrap && terraform destroy`

If you destroy in the reverse order the demo-deploy state bucket disappears
out from under the demo-deploy stack and you lose the ability to manage its
resources via Terraform.

## Cost

~$0.10/month — three S3 versioned objects, an empty DynamoDB table on
PAY_PER_REQUEST billing, and the OIDC provider (free).
