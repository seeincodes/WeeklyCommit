output "dlt_alarm_arn" {
  description = "ARN of the notification_dlt < 1h alarm. Wire to runbooks / dashboards."
  value       = aws_cloudwatch_metric_alarm.notification_dlt_recent.arn
}

output "circuit_breaker_alarm_arns" {
  description = "Map of circuit-breaker name (notification, rcdo) to alarm ARN."
  value       = { for k, v in aws_cloudwatch_metric_alarm.circuit_breaker_open : k => v.arn }
}
