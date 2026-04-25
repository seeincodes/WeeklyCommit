# Monitoring — CloudWatch alarms

Operational alarms for `weekly-commit-service`. Imports nothing AWS-side; expects the consumer to bring an AWS provider block and (optionally) an SNS topic ARN to route alarm transitions.

## What's here

| Alarm | Source metric | Severity | Why |
|-------|---------------|----------|-----|
| `${env}-weekly-commit-notification-dlt-recent` | `weekly_commit.notification.dlt.recent_count` (Micrometer gauge) | page | Any `notification_dlt` row younger than 1h means a notification dropped. Replay via `POST /admin/notifications/dlt/{id}/replay` or investigate `notification-svc`. |
| `${env}-weekly-commit-cb-open-notification` | `resilience4j.circuitbreaker.state{name=notification,state=open}` | page | The Resilience4j breaker for `notification-svc` is OPEN — calls are failing fast. Pages on 2 consecutive 1-min datapoints to avoid HALF_OPEN flapping. |
| `${env}-weekly-commit-cb-open-rcdo` | `resilience4j.circuitbreaker.state{name=rcdo,state=open}` | page | The Resilience4j breaker for `RCDO` is OPEN — picker / hydration calls are short-circuiting. |

## Consumer pattern

```hcl
provider "aws" {
  region = "us-east-1"
}

module "weekly_commit_alarms" {
  source = "../../weekly-commit-module/infra/terraform/monitoring"

  environment                  = "prod"
  alarm_actions_sns_topic_arns = [aws_sns_topic.oncall.arn]
  tags                         = { team = "platform", system = "weekly-commit" }
}
```

## Source-metric prerequisites

For these alarms to receive data points, the backend must:

1. Have `MICROMETER_CLOUDWATCH_ENABLED=true` set on the deployed pod.
2. Have `MICROMETER_CLOUDWATCH_NAMESPACE` matching the Terraform `metrics_namespace` variable (default `WeeklyCommit`).
3. Have IAM permissions for `cloudwatch:PutMetricData` against that namespace.
4. Have `DltMetricsPublisher` running (auto-active; ships in the service jar).
5. Have `resilience4j-micrometer` on the classpath (transitive via `resilience4j-spring-boot3`).

If any are missing, the alarms will show "INSUFFICIENT_DATA" — `treat_missing_data = "notBreaching"` keeps that from paging.

## Dimensions

Both alarms scope to `application=weekly-commit-service` (the value of `management.metrics.tags.application`). Multiple deployments in the same namespace + region can coexist by overriding `application_dimension` per consumer.

## Why no SNS topic in this module

SNS topic ownership belongs with the team that runs the on-call rotation, not with this service. Wiring a topic in here would couple alarm lifecycle to topic lifecycle. The consumer passes ARNs in `alarm_actions_sns_topic_arns` instead.
