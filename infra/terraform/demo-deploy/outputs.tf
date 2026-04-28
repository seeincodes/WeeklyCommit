output "cloudfront_url" {
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
  description = "The live URL. Open this in a browser after the deploy workflow has pushed at least one image and one frontend bundle."
}

output "cloudfront_distribution_id" {
  value       = aws_cloudfront_distribution.main.id
  description = "Used by the deploy workflow to invalidate /index.html on each frontend deploy."
}

output "ecr_repository_url" {
  value       = aws_ecr_repository.backend.repository_url
  description = "Used by the deploy workflow to push the backend image."
}

output "ecs_cluster_name" {
  value       = aws_ecs_cluster.main.name
  description = "Used by the deploy workflow to force-new-deployment."
}

output "ecs_service_name" {
  value       = aws_ecs_service.backend.name
  description = "Used by the deploy workflow to force-new-deployment."
}

output "frontend_s3_bucket" {
  value       = aws_s3_bucket.frontend.id
  description = "Used by the deploy workflow to upload `dist/` contents."
}

output "rds_endpoint" {
  value       = aws_db_instance.main.address
  description = "Hostname of the demo RDS instance. Available for ssh-port-forward debugging if needed; not used by the deployed app (the JDBC URL is in the task definition)."
  sensitive   = false
}

output "alb_dns_name" {
  value       = aws_lb.main.dns_name
  description = "Internal-only ALB DNS. CloudFront uses this; not exposed to end users."
}
