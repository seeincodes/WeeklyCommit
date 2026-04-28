################################################################################
# RDS Postgres 16.4 single-AZ + Secrets Manager-managed master password.
#
# Single-AZ is a deliberate demo deviation from the PRD's Multi-AZ posture.
# Failover drill / Multi-AZ failure-mode rehearsal stays on the production-
# ready follow-up branch.
################################################################################

resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db"
  subnet_ids = aws_subnet.public[*].id
  tags = {
    Name = "${local.name_prefix}-db"
  }
}

resource "aws_db_parameter_group" "postgres16" {
  name   = "${local.name_prefix}-pg16"
  family = "postgres16"

  # Parameters tuned for a tiny demo. log_min_duration_statement at 1000ms
  # surfaces slow queries in CloudWatch logs without flooding on every
  # SELECT. shared_preload_libraries left at default -- the demo doesn't
  # need pg_stat_statements (the rollup perf branch can revisit).
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
}

# Master password. Generated once, stored in Secrets Manager, referenced by
# the ECS task definition. The plaintext never appears in Terraform state
# in cleartext (Secrets Manager handles the cipher); rotate via Secrets
# Manager rotation, not by re-applying Terraform.
resource "random_password" "rds_master" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "rds_password" {
  name                    = "${local.name_prefix}-rds-master-password"
  description             = "Postgres master password for the demo backend. Consumed by the ECS task definition."
  recovery_window_in_days = 0 # demo: no soft-delete grace; production: 7-30
}

resource "aws_secretsmanager_secret_version" "rds_password" {
  secret_id     = aws_secretsmanager_secret.rds_password.id
  secret_string = random_password.rds_master.result
}

resource "aws_db_instance" "main" {
  identifier = "${local.name_prefix}-postgres"

  engine               = "postgres"
  engine_version       = "16.4"
  instance_class       = var.rds_instance_class
  allocated_storage    = var.rds_allocated_storage_gb
  storage_type         = "gp3"
  storage_encrypted    = true
  db_subnet_group_name = aws_db_subnet_group.main.name
  parameter_group_name = aws_db_parameter_group.postgres16.name

  db_name  = "weeklycommit"
  username = "weeklycommit"
  password = random_password.rds_master.result

  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = var.rds_backup_retention_days
  backup_window           = "07:00-08:00"
  maintenance_window      = "Mon:08:00-Mon:09:00"

  # Demo: skip final snapshot on destroy so `terraform destroy` doesn't leave
  # an unreachable named snapshot lingering. Production: set this false and
  # name the final snapshot.
  skip_final_snapshot      = true
  delete_automated_backups = true

  # Single-AZ. Multi-AZ doubles cost (~$26/mo for db.t4g.micro). Demo accepts
  # the unavailability window during patch.
  multi_az = false

  # Demo size: keep deletion protection off so cleanup is fast. Production:
  # turn this on and revoke via a manual DB instance modify.
  deletion_protection = false

  publicly_accessible = false

  performance_insights_enabled = false # extra cost; not needed for demo
  monitoring_interval          = 0     # extra cost; not needed for demo

  apply_immediately = true
}
