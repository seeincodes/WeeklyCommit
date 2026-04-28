variable "aws_region" {
  type        = string
  description = "AWS region. CloudFront's only ACM-cert region is us-east-1; the rest of the stack lives wherever you put it but lifting + shifting region requires a CloudFront cert in us-east-1 anyway. Keep this aligned with the bootstrap module's region."
  default     = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Environment name -- baked into resource names + tags. Single environment in this stack, but the variable lets a fork operate two parallel stacks."
  default     = "demo"
}

variable "image_tag" {
  type        = string
  description = "Container image tag for the backend ECS task. The deploy workflow sets this to the GitHub commit SHA on every push; locally, leave the default and apply will reference whatever's already in ECR (or the placeholder image if the repo is empty)."
  default     = "latest"
}

variable "ecs_task_cpu" {
  type        = number
  description = "Fargate task CPU units. 256 = 0.25 vCPU. Demo runs comfortably on the smallest valid ECS Fargate combo (256/512). Bump if the seeded data set grows or if scheduled jobs come back online."
  default     = 256
}

variable "ecs_task_memory" {
  type        = number
  description = "Fargate task memory in MiB. 512 is the smallest valid pairing with cpu=256. JVM heap caps at 70% of this via JAVA_OPTS in the Dockerfile."
  default     = 512
}

variable "rds_instance_class" {
  type        = string
  description = "RDS instance class. db.t4g.micro is the cheapest Postgres-supported class and fits the demo's working set. Single-AZ deployment per MEMO #11 -- demo deviation from the PRD's Multi-AZ target."
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage_gb" {
  type        = number
  description = "RDS storage in GB. 20GB is RDS's free-tier-friendly minimum on gp3; the demo data fits in <100MB so this is mostly headroom for log + WAL growth."
  default     = 20
}

variable "rds_backup_retention_days" {
  type        = number
  description = "RDS backup retention in days. 7 is the demo deviation from MEMO #11; PRD calls for 7d retention, which fortunately matches."
  default     = 7
}
