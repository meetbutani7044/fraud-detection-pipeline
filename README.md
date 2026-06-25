# Real-Time Fraud Detection Pipeline

[![CI / Docker](https://github.com/meetbutani7044/fraud-detection-pipeline/actions/workflows/ci.yml/badge.svg)](https://github.com/meetbutani7044/fraud-detection-pipeline/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-25-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.7.0-black)

A production-grade, event-driven fraud detection system built with Java, Spring Boot, and Apache Kafka. Transactions flow through a three-service pipeline: ingestion → real-time scoring → persistent alerting.

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Services](#services)
- [Fraud Detection Rules](#fraud-detection-rules)
- [Project Structure](#project-structure)
- [Quick Start — Docker Compose](#quick-start--docker-compose)
- [API Reference](#api-reference)
- [Running Tests](#running-tests)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Terraform — AWS Cloud Infrastructure](#terraform--aws-cloud-infrastructure)
- [CI/CD Pipeline](#cicd-pipeline)
- [Configuration Reference](#configuration-reference)

---

## Architecture

```
  Client
    │
    │  POST /api/v1/transactions
    ▼
┌─────────────────┐        Kafka: transactions        ┌──────────────────┐
│   api-gateway   │ ──────────────────────────────►  │   risk-scorer    │
│   port 8080     │                                   │   port 8081      │
└─────────────────┘                                   └────────┬─────────┘
                                                               │
                                                               │  Kafka: fraud-alerts
                                                               │  (only when a rule fires)
                                                               ▼
                                                      ┌──────────────────┐
  Client                                              │  alert-service   │
    │  GET /api/v1/alerts  ◄─────────────────────────│   port 8082      │
    │                                                 └───────┬──────────┘
    │                                                         │
    │                                              ┌──────────┴──────────┐
    │                                              ▼                     ▼
    │                                         ┌─────────┐         ┌──────────┐
    │                                         │  Redis  │         │Postgres  │
    │                                         │  :6379  │         │  :5432   │
    └────────────────────── cache hit ────────┘                   └──────────┘
                                                                        │
                                                                   persisted
                                                                   long-term
```

**Data flow:**

1. A client posts a transaction to **api-gateway**, which validates it and publishes it to the `transactions` Kafka topic. The client immediately receives `202 ACCEPTED` with a transaction ID.
2. **risk-scorer** consumes each transaction, runs it through the rule engine, and — if a rule fires — publishes a `FraudAlert` to the `fraud-alerts` topic. The Kafka offset is only committed after the alert is durably written.
3. **alert-service** consumes `fraud-alerts`, persists each alert to PostgreSQL (idempotent on transaction ID), and caches the full response in Redis for fast lookup. The REST API exposes the alerts for querying.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 3.4.1 |
| Messaging | Apache Kafka (Confluent 7.7.0) |
| Cache | Redis 7.4 |
| Database | PostgreSQL 16 |
| Containerisation | Docker, Docker Compose |
| Orchestration | Kubernetes (manifests in `k8s/`) |
| Cloud Infrastructure | Terraform → AWS (EKS, RDS, ElastiCache, MSK) |
| CI/CD | GitHub Actions |
| Observability | Spring Actuator, Prometheus metrics |

---

## Services

### api-gateway `port 8080`

REST entry point. Accepts and validates incoming transactions, assigns a UUID transaction ID, and publishes the payload to the `transactions` Kafka topic with idempotent, acks-all producer settings.

### risk-scorer `port 8081`

Kafka consumer on the `transactions` topic. Runs every transaction through a pluggable rule engine — each rule is a `@Component` implementing `FraudRule`. When a rule fires, the service publishes a `FraudAlert` to `fraud-alerts`. Uses Redis for per-account velocity tracking across all replicas. Kafka offset is committed only after the alert publish succeeds.

### alert-service `port 8082`

Kafka consumer on the `fraud-alerts` topic. Persists each alert to PostgreSQL (duplicate transaction IDs are silently dropped for idempotency). Caches the full alert response in Redis (24h TTL) so lookups by transaction ID skip the database on cache hit. Exposes a paginated REST API for querying alerts.

---

## Fraud Detection Rules

Rules are discovered automatically by Spring — adding a new rule only requires creating a `@Component` that implements `FraudRule`. The engine returns the first rule that fires.

| Rule | Trigger | Risk Score | Config Key |
|---|---|---|---|
| `HIGH_AMOUNT` | Transaction amount exceeds threshold | `0.8` | `fraud.rules.high-amount-threshold` (default `10000`) |
| `VELOCITY_EXCEEDED` | Account exceeds N transactions in a time window | `0.9` | `fraud.rules.velocity.max-transactions` (default `5`), `fraud.rules.velocity.window-seconds` (default `60`) |

**Threshold semantics:** `max-transactions: 5` means up to 5 transactions are allowed; the 6th within the window triggers the rule.

---

## Project Structure

```
fraud-detection-pipeline/
├── api-gateway/                  # Spring Boot — REST ingestion + Kafka producer
│   ├── src/main/java/com/frauddetection/gateway/
│   │   ├── controller/           # TransactionController (POST /api/v1/transactions)
│   │   ├── kafka/                # TransactionProducer
│   │   ├── config/               # KafkaConfig (topic declarations)
│   │   └── dto/                  # TransactionRequest, TransactionResponse
│   └── Dockerfile
│
├── risk-scorer/                  # Spring Boot — Kafka consumer + rule engine
│   ├── src/main/java/com/frauddetection/riskscorer/
│   │   ├── consumer/             # TransactionConsumer (manual ack)
│   │   ├── producer/             # FraudAlertProducer
│   │   ├── rules/                # FraudRule, RuleEngine, HighAmountRule, VelocityRule
│   │   └── config/               # KafkaConfig (read_committed consumer)
│   └── Dockerfile
│
├── alert-service/                # Spring Boot — Kafka consumer + persistence + query API
│   ├── src/main/java/com/frauddetection/alertservice/
│   │   ├── consumer/             # FraudAlertConsumer
│   │   ├── controller/           # AlertController (GET /api/v1/alerts/*)
│   │   ├── service/              # AlertService (Postgres + Redis)
│   │   ├── entity/               # FraudAlertEntity (JPA)
│   │   ├── repository/           # FraudAlertRepository
│   │   ├── config/               # KafkaConfig, RedisConfig
│   │   └── dto/                  # FraudAlertEvent, AlertResponse
│   └── Dockerfile
│
├── k8s/                          # Kubernetes manifests
│   ├── namespace.yml
│   ├── configmap.yml
│   ├── secret.yml
│   ├── infrastructure/           # Zookeeper, Kafka, PostgreSQL, Redis StatefulSets
│   └── apps/                     # api-gateway, risk-scorer, alert-service Deployments + HPA
│
├── terraform/                    # AWS infrastructure (EKS, RDS, ElastiCache, MSK)
│   ├── vpc.tf
│   ├── eks.tf
│   ├── rds.tf
│   ├── elasticache.tf
│   ├── msk.tf
│   ├── variables.tf
│   ├── outputs.tf
│   └── terraform.tfvars.example
│
├── .github/workflows/
│   └── ci.yml                    # Test + Docker publish pipeline
│
└── docker-compose.yml            # Full local stack (all services + infrastructure)
```

---

## Quick Start — Docker Compose

### Prerequisites

- Docker Desktop running
- Java 25 + Maven 3.9+ (for local development only)
- Ports `8080`, `8081`, `8082`, `8090`, `9092`, `5432`, `6379` free

### Start the full stack

```bash
git clone https://github.com/meetbutani7044/fraud-detection-pipeline.git
cd fraud-detection-pipeline
docker compose up --build
```

First run builds all three service images (~3–4 minutes). Subsequent starts are fast (cached layers).

**Verify everything is up:**

```bash
curl http://localhost:8080/api/v1/health        # api-gateway
curl http://localhost:8081/actuator/health      # risk-scorer
curl http://localhost:8082/api/v1/alerts/health # alert-service
```

Kafka UI is available at [http://localhost:8090](http://localhost:8090).

---

### Submit a normal transaction

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-123456",
    "amount": 250.00,
    "currency": "USD",
    "merchantId": "MERCH-001",
    "transactionType": "DEBIT",
    "notes": "Coffee shop"
  }' | jq
```

Expected response (`202 Accepted`):
```json
{
  "transactionId": "a3f1c2d4-...",
  "status": "ACCEPTED",
  "message": "Transaction accepted for processing",
  "acceptedAt": "2025-10-01T12:00:00Z"
}
```

### Trigger a HIGH_AMOUNT alert

```bash
curl -s -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-999",
    "amount": 15000.00,
    "currency": "USD",
    "merchantId": "MERCH-VIP",
    "transactionType": "DEBIT"
  }' | jq
```

After a second or two, query the alert:

```bash
curl -s "http://localhost:8082/api/v1/alerts/account/ACC-999" | jq
```

### Trigger a VELOCITY_EXCEEDED alert

Send 6+ transactions from the same account within 60 seconds:

```bash
for i in {1..6}; do
  curl -s -X POST http://localhost:8080/api/v1/transactions \
    -H "Content-Type: application/json" \
    -d "{\"accountId\":\"ACC-VELOCITY\",\"amount\":10.00,\"currency\":\"USD\",\"transactionType\":\"DEBIT\"}" \
    | jq .transactionId
done
```

### Stop the stack

```bash
docker compose down          # keep volumes (data persists)
docker compose down -v       # also remove volumes (clean slate)
```

---

## API Reference

### api-gateway `http://localhost:8080`

#### `POST /api/v1/transactions`

Submit a transaction for fraud scoring.

**Request body:**

| Field | Type | Required | Validation |
|---|---|---|---|
| `accountId` | string | yes | alphanumeric + `_-`, max 64 chars |
| `amount` | decimal | yes | > 0.00, max 16 integer digits, 2 decimal places |
| `currency` | string | yes | 3-letter ISO 4217 code (e.g. `USD`) |
| `merchantId` | string | no | max 64 chars |
| `transactionType` | string | yes | `CREDIT`, `DEBIT`, `TRANSFER`, or `WITHDRAWAL` |
| `notes` | string | no | max 500 chars |

**Responses:**

| Status | Meaning |
|---|---|
| `202 Accepted` | Transaction accepted and queued for scoring |
| `400 Bad Request` | Validation failure — body contains field-level errors |

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC-001","amount":500.00,"currency":"USD","transactionType":"CREDIT"}'
```

---

#### `GET /api/v1/health`

Simple liveness check used by the Kubernetes readiness probe.

```bash
curl http://localhost:8080/api/v1/health
# UP
```

---

### alert-service `http://localhost:8082`

#### `GET /api/v1/alerts`

List all fraud alerts, newest first. Paginated.

| Query param | Default | Description |
|---|---|---|
| `page` | `0` | Zero-based page number |
| `size` | `20` | Page size |

```bash
curl "http://localhost:8082/api/v1/alerts?page=0&size=10" | jq
```

**Response shape:**
```json
{
  "content": [
    {
      "id": 1,
      "transactionId": "a3f1c2d4-...",
      "accountId": "ACC-999",
      "amount": 15000.00,
      "currency": "USD",
      "ruleTriggered": "HIGH_AMOUNT",
      "riskScore": 0.8,
      "detectedAt": "2025-10-01T12:00:01Z",
      "createdAt": "2025-10-01T12:00:01Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

---

#### `GET /api/v1/alerts/account/{accountId}`

List all alerts for a specific account, newest first. Accepts `page` and `size` query params.

```bash
curl "http://localhost:8082/api/v1/alerts/account/ACC-999?page=0&size=20" | jq
```

---

#### `GET /api/v1/alerts/{transactionId}`

Retrieve a single alert by transaction ID. Cache hit returns directly from Redis; miss falls back to PostgreSQL.

```bash
curl "http://localhost:8082/api/v1/alerts/a3f1c2d4-..." | jq
```

| Status | Meaning |
|---|---|
| `200 OK` | Alert found |
| `404 Not Found` | No alert for this transaction (clean transaction or not yet processed) |

---

#### `GET /api/v1/alerts/health`

```bash
curl http://localhost:8082/api/v1/alerts/health
# UP
```

---

#### Actuator endpoints (all services)

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Liveness + readiness details |
| `GET /actuator/prometheus` | Prometheus metrics scrape |
| `GET /actuator/metrics` | Named metric lookup |

---

## Running Tests

Tests use `@WebMvcTest` (web-layer slice) and `@ExtendWith(MockitoExtension.class)` (pure unit tests). No external services (Kafka, Redis, PostgreSQL) are required.

```bash
# Test each service independently
cd api-gateway   && mvn test
cd risk-scorer   && mvn test
cd alert-service && mvn test

# Verify + package (runs tests then builds the JAR)
mvn verify -B --no-transfer-progress
```

> **Windows note:** If your Windows username contains a space (e.g. `C:\Users\John Doe`), Maven Surefire may fail to fork the JVM due to an unquoted `-javaagent` path. The Docker-based build (`mvn package -DskipTests`) is unaffected. CI runs on Ubuntu where all tests pass.

---

## Kubernetes Deployment

The manifests in `k8s/` target any CNCF-conformant cluster (minikube, kind, EKS, GKE, AKS).

### Prerequisites

- `kubectl` configured against your target cluster
- For EKS: `aws eks update-kubeconfig --name <cluster-name> --region <region>`

### Apply in order

```bash
# Namespace and shared config
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/secret.yml

# Infrastructure — Zookeeper must come before Kafka
kubectl apply -f k8s/infrastructure/zookeeper.yml
kubectl apply -f k8s/infrastructure/kafka.yml
kubectl apply -f k8s/infrastructure/postgres.yml
kubectl apply -f k8s/infrastructure/redis.yml

# Application services
kubectl apply -f k8s/apps/

# Or apply everything in one shot (init containers handle startup ordering)
kubectl apply -f k8s/
```

### Verify

```bash
kubectl get pods    -n fraud-detection
kubectl get svc     -n fraud-detection
kubectl get hpa     -n fraud-detection
```

### Key design notes

- **Init containers** in each app pod wait for their dependencies (Kafka, Redis, PostgreSQL) before the main container starts — no manual ordering required.
- **HPA** scales each service between 2–5 replicas at 70% CPU utilisation.
- In cloud environments (EKS/GKE), `LoadBalancer` services receive external IPs automatically. For local clusters (minikube/kind), change `type: LoadBalancer` to `type: NodePort` in `k8s/apps/api-gateway.yml` and `k8s/apps/alert-service.yml`.
- The Kafka and Zookeeper StatefulSets are suitable for dev and staging. **For production, replace with the [Strimzi operator](https://strimzi.io)** or point `KAFKA_BOOTSTRAP_SERVERS` at AWS MSK (see Terraform section).

### Update ConfigMap with cloud service endpoints

After running `terraform apply`, patch the ConfigMap with the managed service addresses and do a rolling restart:

```bash
# Get the endpoints
TF_MSK=$(terraform -chdir=terraform output -raw msk_bootstrap_brokers_plaintext)
TF_RDS=$(terraform -chdir=terraform output -raw rds_endpoint)
TF_REDIS=$(terraform -chdir=terraform output -raw redis_endpoint)

# Edit k8s/configmap.yml, then apply
kubectl apply -f k8s/configmap.yml

# Roll all app deployments to pick up the new values
kubectl rollout restart deployment -n fraud-detection
```

---

## Terraform — AWS Cloud Infrastructure

Provisions a production-grade AWS environment to replace the in-cluster StatefulSets: VPC, EKS, RDS PostgreSQL, ElastiCache Redis, and MSK Kafka.

### What gets created

| Resource | Details |
|---|---|
| VPC | `/16` CIDR, 2 public + 2 private subnets across 2 AZs, single NAT gateway |
| EKS | Kubernetes 1.31, managed node group (`t3.medium`, 2–5 nodes) |
| RDS | PostgreSQL 16, `db.t3.micro`, encrypted at rest, automated backups |
| ElastiCache | Redis 7.1, `cache.t3.micro`, AOF persistence enabled |
| MSK | Kafka 3.6.0, 2 × `kafka.m5.large` brokers, CloudWatch broker logs |

### Prerequisites

- AWS CLI configured (`aws configure`)
- Terraform ≥ 1.7 installed (`terraform -version`)
- An S3 bucket + DynamoDB table for remote state (instructions in `terraform/versions.tf`)

### Deploy

```bash
cd terraform

# 1. Copy example vars and fill in required values
cp terraform.tfvars.example terraform.tfvars
# Set at minimum: db_password
# Optionally: aws_region, environment, instance sizes

# 2. Initialise providers and backend
terraform init

# 3. Preview what will be created
terraform plan

# 4. Apply (takes ~15–20 min — EKS and MSK are slow to provision)
terraform apply
```

### Key outputs

```
eks_cluster_name                  = "fraud-detection-dev-cluster"
msk_bootstrap_brokers_plaintext   = "b-1.fraud-detection...amazonaws.com:9092,..."
rds_endpoint                      = "fraud-detection-dev.xxxx.us-east-1.rds.amazonaws.com:5432"
redis_endpoint                    = "fraud-detection-dev.xxxx.cfg.use1.cache.amazonaws.com"
vpc_id                            = "vpc-0abc..."
private_subnet_ids                = ["subnet-0...", "subnet-0..."]
```

### Tear down

```bash
terraform destroy
```

> **Cost warning:** MSK (`kafka.m5.large`) and EKS worker nodes (`t3.medium`) are not free-tier eligible. Run `terraform destroy` when the environment is not in use.

### Production hardening (not applied by default)

The Terraform code includes inline comments for hardening steps appropriate for production:

- Change `endpoint_public_access = true` → `false` on the EKS cluster and access it via a bastion host
- Change `client_broker = "TLS_PLAINTEXT"` → `"TLS_ONLY"` in MSK encryption config
- Replace the single NAT gateway with one per AZ for high availability
- Use [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/) or Sealed Secrets instead of the `k8s/secret.yml` plaintext placeholder

---

## CI/CD Pipeline

Defined in `.github/workflows/ci.yml`. Runs on every push to `main` and every pull request targeting `main`.

```
push / PR to main
        │
        ├── test (api-gateway)   ─┐
        ├── test (risk-scorer)   ─┼─ parallel, Ubuntu, JDK 25 + Maven cache
        └── test (alert-service) ─┘
                    │
                    │  all three must pass
                    ▼
        ├── docker (api-gateway)   ─┐  push to main only (not on PRs)
        ├── docker (risk-scorer)   ─┼─ build + push to ghcr.io with GHA layer cache
        └── docker (alert-service) ─┘
```

### Published images

On every merge to `main`, three images are pushed to GitHub Container Registry:

```
ghcr.io/meetbutani7044/fraud-detection-pipeline/api-gateway:latest
ghcr.io/meetbutani7044/fraud-detection-pipeline/api-gateway:sha-<short-sha>

ghcr.io/meetbutani7044/fraud-detection-pipeline/risk-scorer:latest
ghcr.io/meetbutani7044/fraud-detection-pipeline/risk-scorer:sha-<short-sha>

ghcr.io/meetbutani7044/fraud-detection-pipeline/alert-service:latest
ghcr.io/meetbutani7044/fraud-detection-pipeline/alert-service:sha-<short-sha>
```

Images use a two-stage Dockerfile: build stage (JDK 25 + Maven) and runtime stage (JRE 25, non-root user, `-XX:MaxRAMPercentage=75.0`).

---

## Configuration Reference

All settings are externalised via environment variables. Defaults in each service's `application.yml` work for local development with Docker Compose.

### api-gateway

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |

### risk-scorer

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `REDIS_HOST` | `localhost` | Redis host for velocity tracking |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password (leave empty if no auth) |

Fraud rule thresholds (set in `application.yml` or via ConfigMap):

| Property | Default | Description |
|---|---|---|
| `fraud.rules.high-amount-threshold` | `10000` | Amount above which HIGH_AMOUNT fires |
| `fraud.rules.velocity.max-transactions` | `5` | Allowed transactions per window |
| `fraud.rules.velocity.window-seconds` | `60` | Velocity window in seconds |

### alert-service

| Variable | Default | Description |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `DB_URL` | `jdbc:postgresql://localhost:5432/frauddb` | PostgreSQL JDBC URL |
| `DB_USERNAME` | `fraud` | PostgreSQL username |
| `DB_PASSWORD` | `fraud_secret` | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password |

Alert cache settings:

| Property | Default | Description |
|---|---|---|
| `fraud.alert.redis-ttl-hours` | `24` | Alert cache TTL in Redis |
| `fraud.alert.recent-per-account` | `50` | Max recent alert IDs kept per account |

---

## Local Port Map

| Service | Port | URL |
|---|---|---|
| api-gateway | `8080` | `http://localhost:8080` |
| risk-scorer | `8081` | `http://localhost:8081/actuator/health` |
| alert-service | `8082` | `http://localhost:8082` |
| Kafka UI | `8090` | `http://localhost:8090` |
| Kafka broker | `9092` | `localhost:9092` |
| PostgreSQL | `5432` | `localhost:5432` (db: `frauddb`, user: `fraud`) |
| Redis | `6379` | `localhost:6379` |
