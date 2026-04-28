################################################################################
# Public-only VPC. The ECS task and RDS instance both live in public subnets so
# we can skip the NAT gateway (~$32/mo). Trade-off: the RDS instance has a
# public IP, but its security group only allows ingress from the ECS task SG,
# so it's not actually internet-reachable. ECS pulls images from ECR via the
# IGW directly.
#
# This shape is appropriate for a demo. PRD's production posture (group 14
# follow-up) should use private subnets with a NAT gateway or VPC endpoints
# for the ECR + Secrets Manager reach. Documented in MEMO #11.
################################################################################

resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

# Two public subnets, one per AZ. ALB requires >=2 AZs. ECS task can run in
# either; the service definition lets ECS pick.
resource "aws_subnet" "public" {
  count = length(local.azs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${local.azs[count.index]}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ---------------- Security groups ----------------

# ALB: ingress from the world on 80, egress to the ECS task SG on 8080.
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb"
  description = "ALB ingress 80 from world; egress to ECS tasks on 8080."
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from world (CloudFront origin)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All egress -- restricted at target via the ECS SG"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

# ECS task: ingress from the ALB SG on 8080 only; full egress (image pulls,
# Secrets Manager, etc.).
resource "aws_security_group" "ecs_task" {
  name        = "${local.name_prefix}-ecs-task"
  description = "ECS task ingress only from ALB on 8080; full egress."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From ALB on app port"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "Full egress for image pulls + Secrets Manager + RDS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-ecs-task"
  }
}

# RDS: ingress from the ECS task SG on 5432 only.
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "Postgres ingress only from ECS task SG."
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from ECS task"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }

  # No egress rule needed -- RDS doesn't initiate outbound. Default is "deny
  # all egress" when no rule is specified, which is what we want.

  tags = {
    Name = "${local.name_prefix}-rds"
  }
}
