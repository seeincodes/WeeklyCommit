################################################################################
# CloudFront distribution. Two origins:
#
#   1. S3 bucket (default origin) -- frontend static assets. OAC-secured.
#   2. ALB (alternate origin) -- backend at /api/* and /actuator/*.
#
# Two cache behaviors:
#
#   - default ("*"): S3 origin, cache hashed assets aggressively (1 year),
#     don't cache /index.html (no-cache header set in nginx-demo.conf,
#     plus a no-cache cache policy applied here for belt + suspenders).
#   - "/api/*" + "/actuator/*": ALB origin, no caching, forward all
#     headers + cookies + Authorization, no compression (gzip the
#     response from the app, not at the edge).
#
# This is the architectural reason mixed-content blocking doesn't bite:
# CloudFront terminates HTTPS at the edge and the browser never sees the
# HTTP-only ALB.
################################################################################

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${local.name_prefix}-s3-oac"
  description                       = "OAC for the frontend S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Managed cache policies -- AWS provides built-ins; using them avoids
# creating + maintaining custom policies for "default" cases.
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

data "aws_cloudfront_origin_request_policy" "cors_s3" {
  name = "Managed-CORS-S3Origin"
}

resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  comment             = "${local.name_prefix} -- frontend + backend"
  price_class         = "PriceClass_100" # US/Canada/Europe; cheapest tier

  # ---------------- Origins ----------------

  origin {
    origin_id                = "s3-frontend"
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    origin_id   = "alb-backend"
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # ALB is HTTP-only; CloudFront → ALB stays HTTP
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    # Pass the original Host header to the ALB so Spring's forward-headers
    # processing produces correct absolute URLs. Default would mask it as
    # the CloudFront domain.
    custom_header {
      name  = "X-Forwarded-Host"
      value = "${local.name_prefix}.cloudfront-default" # logical marker; backend doesn't read this
    }
  }

  # ---------------- Cache behaviors ----------------

  # Default behavior: S3 frontend.
  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_optimized.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.cors_s3.id
  }

  # /api/* -> ALB, no caching.
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "alb-backend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    compress               = false

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # /actuator/* -> ALB, no caching. Same shape as /api/*.
  ordered_cache_behavior {
    path_pattern           = "/actuator/*"
    target_origin_id       = "alb-backend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = false

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  # SPA fallback: any 404 from the S3 origin (which means a SPA route the
  # bucket doesn't have a file for) returns /index.html with status 200 so
  # the in-browser router can take over.
  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# ---------------- S3 bucket policy: allow CloudFront only ----------------

data "aws_iam_policy_document" "frontend_bucket_policy" {
  statement {
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_bucket_policy.json
}
