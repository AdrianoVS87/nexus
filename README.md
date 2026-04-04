![Build](https://img.shields.io/github/actions/workflow/status/AdrianoVS87/nexus/ci.yml?branch=main&style=flat-square)
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?style=flat-square)
![License](https://img.shields.io/github/license/AdrianoVS87/nexus?style=flat-square)

# Nexus

Event-driven e-commerce microservices platform demonstrating distributed systems mastery with Saga orchestration, CQRS, and real-time order tracking.

## Architecture

```mermaid
graph TB
    subgraph Client
        WEB[React Frontend]
    end

    subgraph API Layer
        GW[API Gateway<br/>Spring Cloud Gateway<br/>JWT + Rate Limiting]
    end

    subgraph Services
        OS[Order Service<br/>Saga Orchestrator]
        PS[Payment Service<br/>Idempotent Processing]
        IS[Inventory Service<br/>CQRS + Optimistic Locking]
        NS[Notification Service<br/>WebSocket + Email]
    end

    subgraph Messaging
        K[Apache Kafka<br/>KRaft Mode]
    end

    subgraph Data
        PG[(PostgreSQL 16<br/>Schema-per-Service)]
        RD[(Redis 7<br/>Cache + CQRS Read Model)]
    end

    subgraph Observability
        PROM[Prometheus]
        GRAF[Grafana]
    end

    WEB -->|HTTP/WS| GW
    GW -->|/orders/**| OS
    GW -->|/products/**| IS

    OS -->|OrderCreated<br/>PaymentRequested| K
    PS -->|PaymentCompleted<br/>PaymentFailed| K
    IS -->|InventoryReserved<br/>InventoryInsufficient| K
    NS -.->|WebSocket| WEB

    K -->|PaymentRequested| PS
    K -->|InventoryReserveRequested| IS
    K -->|OrderConfirmed<br/>OrderCancelled| NS

    OS --> PG
    PS --> PG
    IS --> PG
    IS --> RD
    GW --> RD

    OS -.->|metrics| PROM
    PS -.->|metrics| PROM
    IS -.->|metrics| PROM
    PROM --> GRAF
```

## Saga Flow

### Happy Path

```mermaid
sequenceDiagram
    participant C as Client
    participant O as Order Service
    participant K as Kafka
    participant P as Payment Service
    participant I as Inventory Service
    participant N as Notification Service

    C->>O: POST /orders
    O->>O: Create order (PENDING)
    O->>K: OrderCreated
    O->>K: PaymentRequested
    K->>P: PaymentRequested
    P->>P: Process payment
    P->>K: PaymentCompleted
    K->>O: PaymentCompleted
    O->>K: InventoryReserveRequested
    K->>I: InventoryReserveRequested
    I->>I: Reserve stock (optimistic lock)
    I->>K: InventoryReserved
    K->>O: InventoryReserved
    O->>O: Update order (CONFIRMED)
    O->>K: OrderConfirmed
    K->>N: OrderConfirmed
    N->>C: WebSocket notification
```

### Compensation (Payment Failed)

```mermaid
sequenceDiagram
    participant O as Order Service
    participant K as Kafka
    participant P as Payment Service

    O->>K: PaymentRequested
    K->>P: PaymentRequested
    P->>P: Payment fails
    P->>K: PaymentFailed
    K->>O: PaymentFailed
    O->>O: Update order (CANCELLED)
    O->>K: OrderCancelled
```

### Compensation (Inventory Insufficient)

```mermaid
sequenceDiagram
    participant O as Order Service
    participant K as Kafka
    participant I as Inventory Service
    participant P as Payment Service

    O->>K: InventoryReserveRequested
    K->>I: InventoryReserveRequested
    I->>I: Insufficient stock
    I->>K: InventoryInsufficient
    K->>O: InventoryInsufficient
    O->>O: Initiate refund
    O->>K: PaymentRefundRequested
    K->>P: PaymentRefundRequested
    P->>P: Process refund
    O->>O: Update order (CANCELLED)
    O->>K: OrderCancelled
```

## Quick Start

```bash
# Clone and start everything — Docker Compose handles the rest
git clone https://github.com/AdrianoVS87/nexus.git
cd nexus
docker compose up -d

# Verify services are healthy
curl http://localhost:8080/actuator/health | jq .

# Open the frontend
open http://localhost:5173
```

## Happy Path — End to End

```bash
# 1. List available products
curl -s http://localhost:8080/api/v1/products | jq .

# 2. Place an order
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "productName": "Mechanical Keyboard",
        "quantity": 1,
        "unitPrice": 149.99
      }
    ]
  }' | jq .

# 3. Check order status (replace ORDER_ID with the id from step 2)
curl -s http://localhost:8080/api/v1/orders/{ORDER_ID} | jq .

# 4. Connect to WebSocket for real-time updates
websocat ws://localhost:8080/ws/orders
```

## Key Design Decisions

**Orchestration-based Saga over Choreography.**
The Order Service acts as a central orchestrator that drives each saga step in sequence. This makes the transaction flow explicit, easy to trace, and straightforward to extend with new compensation logic. In a choreography approach, the flow is scattered across consumers and harder to reason about when debugging production incidents.

**CQRS for the Inventory Service.**
Product catalog reads vastly outnumber writes. By separating the write model (PostgreSQL with optimistic locking) from the read model (Redis), the system serves high-throughput product listing queries from cache without contending with stock reservation writes.

**Idempotency keys for payment processing.**
Network partitions and consumer retries are inevitable in a distributed system. Every payment request carries a unique idempotency key so the Payment Service can safely deduplicate retries and guarantee exactly-once processing semantics.

**Kafka in KRaft mode (no ZooKeeper).**
KRaft eliminates the operational overhead of running a separate ZooKeeper ensemble. The metadata quorum runs inside the Kafka brokers themselves, simplifying deployment, reducing infrastructure footprint, and aligning with the Apache Kafka project's long-term direction.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.4 |
| API Gateway | Spring Cloud Gateway | 2024.0 |
| Messaging | Apache Kafka (KRaft) | 3.7 |
| Database | PostgreSQL | 16 |
| Cache/CQRS | Redis | 7 |
| Frontend | React + TypeScript + Vite | 18 / 5 / 6 |
| Tracing | OpenTelemetry | 1.36 |
| Metrics | Micrometer + Prometheus | - |
| Resilience | Resilience4j | 2.2 |
| Migrations | Flyway | 10 |
| Testing | JUnit 5 + Testcontainers | - |
| CI/CD | GitHub Actions | - |

## API Documentation

See [docs/API.md](docs/API.md) for the full API reference.

## Project Structure

```
nexus/
├── order-service/          # Saga orchestrator + order management
├── payment-service/        # Payment processing with idempotency
├── inventory-service/      # CQRS stock management
├── notification-service/   # WebSocket + email notifications
├── api-gateway/            # JWT auth + rate limiting + routing
├── web/                    # React frontend
├── infra/                  # Prometheus + Grafana configs
├── .github/workflows/      # CI pipeline
└── docker-compose.yml      # Full stack orchestration
```

## License

[MIT](LICENSE)
