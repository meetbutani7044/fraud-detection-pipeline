# ── Security Group ────────────────────────────────────────────────────────────

resource "aws_security_group" "msk" {
  name        = "${local.name_prefix}-msk-sg"
  description = "Allow Kafka broker access from private subnets"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Kafka plaintext from private subnets"
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private[*].cidr_block
  }

  ingress {
    description = "Kafka TLS from private subnets"
    from_port   = 9094
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private[*].cidr_block
  }

  ingress {
    description = "ZooKeeper (used internally by MSK)"
    from_port   = 2181
    to_port     = 2181
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private[*].cidr_block
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-msk-sg" }
}

# ── CloudWatch Log Group ──────────────────────────────────────────────────────

resource "aws_cloudwatch_log_group" "msk_broker" {
  name              = "/fraud-detection/msk/broker-logs"
  retention_in_days = 7

  tags = { Name = "${local.name_prefix}-msk-broker-logs" }
}

# ── MSK Configuration ─────────────────────────────────────────────────────────

resource "aws_msk_configuration" "main" {
  name              = "${local.name_prefix}-kafka-config"
  kafka_versions    = [var.msk_kafka_version]

  server_properties = <<-EOT
    auto.create.topics.enable=false
    default.replication.factor=2
    min.insync.replicas=1
    num.partitions=3
    log.retention.hours=168
    offsets.topic.replication.factor=2
    transaction.state.log.replication.factor=2
    transaction.state.log.min.isr=1
  EOT
}

# ── MSK Cluster ───────────────────────────────────────────────────────────────

resource "aws_msk_cluster" "main" {
  cluster_name           = "${local.name_prefix}-kafka"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.msk_broker_count

  broker_node_group_info {
    instance_type   = var.msk_broker_instance_type
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_broker_storage_gb
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"   # allow both; enforce TLS_ONLY in prod
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk_broker.name
      }
    }
  }

  tags = { Name = "${local.name_prefix}-kafka" }
}
