variable "environment" {
  description = "Deployment environment (dev, staging, prod). Used in alarm names + tags."
  type        = string
}

variable "metrics_namespace" {
  description = <<-EOT
    CloudWatch namespace that the Micrometer CloudWatch registry publishes under.
    Must match the value of `management.cloudwatch.metrics.export.namespace` in
    weekly-commit-service application.yml (default: WeeklyCommit).
  EOT
  type        = string
  default     = "WeeklyCommit"
}

variable "application_dimension" {
  description = <<-EOT
    Value of the `application` tag that Micrometer attaches to every metric
    (set by `management.metrics.tags.application = $${spring.application.name}`).
    Used as a CloudWatch dimension to scope alarms to this service.
  EOT
  type        = string
  default     = "weekly-commit-service"
}

variable "alarm_actions_sns_topic_arns" {
  description = <<-EOT
    SNS topic ARNs that receive alarm transitions (OK -> ALARM and back).
    Empty list disables notifications -- useful when bootstrapping an environment
    before the on-call topic exists. Wire to the platform-team paging topic in prod.
  EOT
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Tags applied to every alarm. Inherits any inherited-from-org defaults via the provider."
  type        = map(string)
  default     = {}
}
