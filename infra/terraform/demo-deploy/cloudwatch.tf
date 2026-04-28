################################################################################
# CloudWatch alarms. Demo posture: only the must-page-on-fail alarms.
# Production target adds DLT < 1h, scheduled-job failures, etc.
################################################################################

# RDS CPU > 80% sustained for 10 minutes -- the demo task is small enough
# that anything other than steady idle CPU usage means trouble.
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "${local.name_prefix}-rds-cpu-high"
  alarm_description   = "RDS CPU > 80% for 10 minutes -- check for runaway query"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 10
  threshold           = 80
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 60
  statistic           = "Average"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }

  treat_missing_data = "notBreaching"
}

# ECS service unhealthy: less than the desired_count of running tasks for 5
# minutes. Catches crash loops, OOM kills, image-pull failures.
resource "aws_cloudwatch_metric_alarm" "ecs_running_tasks_low" {
  alarm_name          = "${local.name_prefix}-ecs-running-low"
  alarm_description   = "ECS service has < desired_count running tasks for 5 minutes"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 5
  threshold           = 1
  metric_name         = "RunningTaskCount"
  namespace           = "ECS/ContainerInsights"
  period              = 60
  statistic           = "Minimum"

  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.backend.name
  }

  treat_missing_data = "breaching" # missing data == probably bad
}
