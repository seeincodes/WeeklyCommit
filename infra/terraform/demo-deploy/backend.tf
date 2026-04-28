################################################################################
# Remote state. Bucket + lock table are provisioned by infra/terraform/bootstrap;
# the bootstrap apply prints `tf_state_bucket` + `tf_lock_table` outputs.
#
# The values below are placeholders. AFTER bootstrap apply, fill in:
#   bucket         = "weekly-commit-tfstate-<your-account-id>"
#   dynamodb_table = "weekly-commit-tflock"
#
# OR pass via -backend-config flags from the CI workflow:
#   terraform init \
#     -backend-config="bucket=weekly-commit-tfstate-<acct>" \
#     -backend-config="key=demo-deploy/terraform.tfstate" \
#     -backend-config="region=us-east-1" \
#     -backend-config="dynamodb_table=weekly-commit-tflock"
#
# Keeping placeholders + supporting -backend-config means the same backend
# block works for both local apply and CI apply. Don't hardcode the account
# id here -- the deploy-demo.yml workflow injects it.
################################################################################

terraform {
  backend "s3" {
    # bucket         = filled in via -backend-config from CI; left blank here.
    key     = "demo-deploy/terraform.tfstate"
    region  = "us-east-1"
    encrypt = true
    # dynamodb_table = filled in via -backend-config from CI; left blank here.
  }
}
