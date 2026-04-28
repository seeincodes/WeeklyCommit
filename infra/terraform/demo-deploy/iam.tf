################################################################################
# IAM roles for ECS:
#
#   - task_execution_role: assumed by the ECS agent itself to pull images from
#     ECR, write logs to CloudWatch, and read Secrets Manager secrets at task
#     start. AWS-managed policy + inline secret-read.
#
#   - task_role: assumed by the running container. The application makes no
#     AWS API calls in demo mode, so this is empty. Keeping the role around
#     so the production target can attach S3 / SQS / etc. without a
#     restructure.
################################################################################

# ---------------- Task execution role ----------------

# ECS uses sts:AssumeRole (not AssumeRoleWithWebIdentity) for its task roles.
data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name_prefix}-task-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Read the RDS password from Secrets Manager at task start.
data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_secretsmanager_secret.rds_password.arn,
    ]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  role   = aws_iam_role.task_execution.id
  name   = "secrets-read"
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

# ---------------- Task role (the application's identity) ----------------

resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  description        = "Application identity. Currently empty -- demo backend makes no AWS API calls. Production target will attach S3 / SQS / etc. here."
}
