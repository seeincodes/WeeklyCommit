################################################################################
# DLT row < 1h alarm (PRD [MVP14], MEMO decision #2, ADR-0002).
#
# Source metric: `weekly_commit.notification.dlt.recent_count` -- a Micrometer
# gauge published every 60s by DltMetricsPublisher in weekly-commit-service.
# Value is the count of NotificationDlt rows whose created_at is within the
# last hour. Any positive value means a notification has dropped to DLT
# recently and ops needs to look (or replay via POST /admin/notifications/dlt/{id}/replay).
#
# Threshold: > 0 for 1 datapoint over 1 minute. Single-datapoint trigger because
# even a single dropped notification on a 175-employee org is a real incident.
################################################################################

resource "aws_cloudwatch_metric_alarm" "notification_dlt_recent" {
  alarm_name          = "${var.environment}-weekly-commit-notification-dlt-recent"
  alarm_description   = "Any notification_dlt row created in the last hour. Replay via POST /admin/notifications/dlt/{id}/replay or investigate notification-svc."
  namespace           = var.metrics_namespace
  metric_name         = "weekly_commit.notification.dlt.recent_count"
  statistic           = "Maximum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"

  dimensions = {
    application = var.application_dimension
  }

  alarm_actions = var.alarm_actions_sns_topic_arns
  ok_actions    = var.alarm_actions_sns_topic_arns

  tags = merge(
    var.tags,
    {
      Component = "weekly-commit-service"
      Severity  = "page"
      Runbook   = "docs/MEMO.md#2-synchronous-notification-with-dlt-fallback-not-outbox-pattern"
    },
  )
}

################################################################################
# Circuit-breaker-open alarms.
#
# Source metric: `resilience4j.circuitbreaker.state` -- gauge auto-published by
# resilience4j-micrometer (transitive via resilience4j-spring-boot3). Value 1
# when in the labeled state. Two alarms: notification + rcdo.
#
# Threshold: > 0 for 2 consecutive datapoints over 1 minute each. Two datapoints
# avoids alarming on a single transient HALF_OPEN test that briefly trips OPEN.
# 2 minutes total dwell is short enough to catch real outages quickly.
################################################################################

locals {
  circuit_breakers = {
    notification = {
      severity = "page"
      runbook  = "docs/adr/0002-notification-svc-contract.md"
    }
    rcdo = {
      severity = "page"
      runbook  = "docs/adr/0001-rcdo-contract.md"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "circuit_breaker_open" {
  for_each = local.circuit_breakers

  alarm_name          = "${var.environment}-weekly-commit-cb-open-${each.key}"
  alarm_description   = "Resilience4j circuit breaker '${each.key}' is OPEN -- upstream is failing fast. Check ${each.value.runbook}."
  namespace           = var.metrics_namespace
  metric_name         = "resilience4j.circuitbreaker.state"
  statistic           = "Maximum"
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  period              = 60
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  treat_missing_data  = "notBreaching"

  dimensions = {
    application = var.application_dimension
    name        = each.key
    state       = "open"
  }

  alarm_actions = var.alarm_actions_sns_topic_arns
  ok_actions    = var.alarm_actions_sns_topic_arns

  tags = merge(
    var.tags,
    {
      Component = "weekly-commit-service"
      Severity  = each.value.severity
      Runbook   = each.value.runbook
    },
  )
}
