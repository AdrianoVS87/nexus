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
# Clone the repository
git clone https://github.com/AdrianoVS87/nexus.git
cd nexus

# Start all services + infrastructure
docker-compose up -d

# Verify services are healthy
curl http://localhost:8080/actuator/health  # API Gateway
curl http://localhost:8081/actuator/health  # Order Service
curl http://localhost:8082/actuator/health  # Payment Service
curl http://localhost:8083/actuator/health  # Inventory Service

# Open frontend
open http://localhost:5173
```

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

See [docs/API.md](docs/API.md) for full API reference.

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
