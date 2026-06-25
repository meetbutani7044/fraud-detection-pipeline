terraform {
  required_version = ">= 1.7"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Uncomment and configure for team use — state must not live on a laptop.
  # backend "s3" {
  #   bucket         = "fraud-detection-tf-state"
  #   key            = "fraud-detection/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "fraud-detection-tf-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
