################################################################################
# Weekly Commit Module — operational alarms (CloudWatch).
#
# Scoped to the weekly-commit-service backend. Fits inside a larger Terraform
# stack as a child module:
#
#   module "weekly_commit_alarms" {
#     source                          = "../infra/terraform/monitoring"
#     environment                     = "prod"
#     alarm_actions_sns_topic_arns    = [aws_sns_topic.platform_oncall.arn]
#     tags                            = local.standard_tags
#   }
#
# This file is intentionally empty -- composition lives in cloudwatch_alarms.tf
# and the consumer is expected to bring its own provider block.
################################################################################
