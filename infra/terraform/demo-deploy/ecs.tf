################################################################################
# ECS Fargate cluster + service + task definition. Single task; no autoscaling.
#
# IMPORTANT: First-apply chicken-and-egg. The task definition references
# `local.backend_image` which is `<ecr-uri>:<tag>`. Until at least one image
# is pushed to ECR, the service can't start a task. Two ways to handle this:
#
#   1. Apply WITHOUT the aws_ecs_service first (target the task definition
#      and prerequisites only), push an image via the GitHub Actions deploy
#      workflow (or `docker push`), then apply again to create the service.
#
#   2. Set `var.image_tag` to a placeholder image known to exist (e.g. by
#      pushing a single tag manually) and apply normally. The service will
#      keep restarting the placeholder until the deploy workflow runs.
#
# The README/runbook documents option 1. Both are valid; both end up at the
# same steady state once the deploy workflow has run.
################################################################################

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${local.name_prefix}/backend"
  retention_in_days = 14
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = local.backend_image
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      # Plain-text env vars: profile activation, DB URL, AUTH0 placeholders
      # (overridden by the e2e profile's bean), demo-specific knobs.
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "e2e,demo" },
        # JDBC URL points at the RDS instance. Hostname comes from RDS's
        # endpoint output; port + DB name are static.
        {
          name  = "SPRING_DATASOURCE_URL"
          value = "jdbc:postgresql://${aws_db_instance.main.address}:5432/${aws_db_instance.main.db_name}"
        },
        { name = "SPRING_DATASOURCE_USERNAME", value = aws_db_instance.main.username },
        # AUTH0_* placeholders satisfy Spring's property resolver at boot.
        # The e2e profile's E2eJwtDecoderConfig overrides the resource
        # server's JwtDecoder so these are never used.
        { name = "AUTH0_ISSUER_URI", value = "http://demo-no-issuer/" },
        { name = "AUTH0_AUDIENCE", value = "demo-no-audience" },
        # In-process RCDO stub on the same JVM at /rcdo. The localhost
        # loopback hits the same task instance.
        { name = "RCDO_BASE_URL", value = "http://localhost:8080" },
        # Background timers off in the demo so seeded plans don't drift.
        { name = "SCHEDULED_JOBS_ENABLED", value = "false" },
        { name = "DEMO_SEED", value = "true" },
        # Heap caps at 70% of 512 MiB. Override JAVA_OPTS via task overrides
        # if memory pressure becomes an issue (Sentry will surface OOMs first).
        { name = "JAVA_OPTS", value = "-XX:MaxRAMPercentage=70 -XX:MaxGCPauseMillis=200" },
      ]

      # Sourced from Secrets Manager at task start. ECS injects the secret
      # value into the env var before the container starts; the application
      # sees a plain SPRING_DATASOURCE_PASSWORD env var.
      secrets = [
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = aws_secretsmanager_secret.rds_password.arn
        },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "backend"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q UP || exit 1"]
        interval    = 15
        timeout     = 5
        retries     = 5
        startPeriod = 90
      }
    }
  ])
}

resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = true # public subnet without NAT, so task pulls images via IGW
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = 8080
  }

  # Wait for at least one healthy task before considering the service
  # "stable." Default is 0; 60s gives the JVM headroom on first boot.
  health_check_grace_period_seconds = 90

  deployment_minimum_healthy_percent = 0   # demo: tolerate full bring-down
  deployment_maximum_percent         = 200 # rolling deploy creates new task before killing old

  # Wait for the listener to exist before creating the service. Without this
  # explicit dep, race conditions during apply produce "target group ARN
  # not associated with an ALB" errors.
  depends_on = [aws_lb_listener.http]

  # Don't fight the deploy workflow's image updates -- it sets the task
  # definition's image tag via aws ecs update-service, which Terraform
  # would then revert on the next apply.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}
