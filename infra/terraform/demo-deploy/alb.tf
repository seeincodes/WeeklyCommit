################################################################################
# Application Load Balancer in front of the ECS task. CloudFront's /api/*
# behavior routes to this ALB; the ALB forwards to the ECS target group.
#
# HTTP-only on the ALB. CloudFront terminates HTTPS at the edge with its
# default *.cloudfront.net cert; the leg from CloudFront to the ALB stays
# HTTP. For a demo with synthetic data this is acceptable; production should
# use ACM + HTTPS on the ALB too.
################################################################################

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]

  # Demo accepts the bring-down on destroy. Production keeps deletion_
  # protection on and revokes via a manual modify.
  enable_deletion_protection = false

  idle_timeout = 60
}

resource "aws_lb_target_group" "backend" {
  name        = "${local.name_prefix}-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    path                = "/actuator/health/readiness"
    port                = "8080"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 15
    matcher             = "200"
  }

  # Default deregistration timeout is 300s; 30s is enough for the demo and
  # makes deploys feel snappier.
  deregistration_delay = 30
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
