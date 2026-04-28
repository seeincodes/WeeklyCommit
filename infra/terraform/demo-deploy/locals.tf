data "aws_caller_identity" "current" {}
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name_prefix = "weekly-commit-${var.environment}"

  # Pick the first two AZs in the region. Two is enough for an ALB (which
  # requires >=2 subnets in different AZs) and for the RDS subnet group
  # (which also requires >=2). Keeping it at two minimizes NAT / EIP cost.
  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  account_id     = data.aws_caller_identity.current.account_id
  ecr_repo_name  = "weekly-commit-service"
  ecr_repo_uri   = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${local.ecr_repo_name}"
  backend_image  = "${local.ecr_repo_uri}:${var.image_tag}"
  s3_bucket_name = "${local.name_prefix}-frontend-${local.account_id}"

  # Common tags for resources whose own tag system isn't covered by the
  # provider's default_tags (e.g. the ECS task definition's `tags` is
  # separate from the container-level tag dict).
  tags = {
    Project     = "weekly-commit"
    Environment = var.environment
    ManagedBy   = "terraform"
    Stack       = "demo-deploy"
  }
}
