################################################################################
# S3 bucket for the frontend static assets. CloudFront accesses it via Origin
# Access Control (OAC) -- the bucket policy allows GetObject ONLY from this
# specific CloudFront distribution. Public access is blocked.
################################################################################

resource "aws_s3_bucket" "frontend" {
  bucket        = local.s3_bucket_name
  force_destroy = true # demo: tolerate `terraform destroy` blowing through objects
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Versioning OFF -- the deploy workflow uploads content-hashed filenames, so
# overwriting is rare and version explosion isn't a concern.

# Bucket policy is defined in cloudfront.tf because it references the
# CloudFront distribution ARN, which is created there.
