# ── Security Group ────────────────────────────────────────────────────────────

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-redis-sg"
  description = "Allow Redis access from private subnets"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Redis from private subnets"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private[*].cidr_block
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name_prefix}-redis-sg" }
}

# ── Subnet Group ─────────────────────────────────────────────────────────────

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name_prefix}-redis-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${local.name_prefix}-redis-subnet-group" }
}

# ── Parameter Group ───────────────────────────────────────────────────────────

resource "aws_elasticache_parameter_group" "redis7" {
  name   = "${local.name_prefix}-redis7"
  family = "redis7"

  parameter {
    name  = "appendonly"
    value = "yes"   # AOF persistence — matches our Docker and K8s config
  }

  tags = { Name = "${local.name_prefix}-redis7" }
}

# ── ElastiCache Cluster ───────────────────────────────────────────────────────

resource "aws_elasticache_cluster" "main" {
  cluster_id           = "${local.name_prefix}-redis"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = aws_elasticache_parameter_group.redis7.name
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  snapshot_retention_limit = var.environment == "prod" ? 5 : 0
  snapshot_window          = "02:00-03:00"
  maintenance_window       = "sun:04:00-sun:05:00"

  tags = { Name = "${local.name_prefix}-redis" }
}
