terraform {
  required_version = ">= 1.5.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "weekly-commit"
      Environment = "demo"
      ManagedBy   = "terraform"
      Stack       = "bootstrap"
    }
  }
}

variable "aws_region" {
  type        = string
  description = "AWS region for the bootstrap resources. Must match the demo-deploy stack region."
  default     = "us-east-1"
}
