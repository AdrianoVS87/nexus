# Contributing to Nexus

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Docker & Docker Compose
- Node.js 20+ with npm

## Getting Started

```bash
git clone https://github.com/AdrianoVS87/nexus.git
cd nexus

# Start infrastructure
docker compose up -d postgres redis kafka

# Build all modules
mvn clean install -DskipTests

# Run a specific service
cd order-service && mvn spring-boot:run

# Or start everything
docker compose up -d
```

## Project Structure

```
nexus/
├── nexus-common/           # Shared events + correlation filter (library)
├── order-service/          # Saga orchestrator — port 8081
├── payment-service/        # Payment processing — port 8082
├── inventory-service/      # CQRS + product CRUD — port 8083
├── notification-service/   # WebSocket + history — port 8084
├── api-gateway/            # JWT + rate limiting — port 8080
└── web/                    # React frontend — port 5173 (dev) / 80 (Docker)
```

## Development Workflow

### Branch naming

```
feat/short-description    # New feature
fix/short-description     # Bug fix
refactor/short-description # Refactoring
test/short-description    # Test additions
docs/short-description    # Documentation
```

### Commit messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(order): add user-initiated cancellation endpoint
fix(kafka): configure explicit producer acks
test(web): add Vitest tests for ProductCatalog
refactor: extract nexus-common module
docs: update API reference
chore: add .dockerignore
ci: add Redis service to GitHub Actions
```

Scope is the module name without `-service` suffix.

### Running Tests

```bash
# Backend (all modules)
mvn test

# Single module
mvn test -pl order-service -am

# Frontend
cd web && npm test

# Frontend watch mode
cd web && npm run test:watch
```

### Adding a New Event

1. Create the record in `nexus-common/src/main/java/com/nexus/common/event/`
2. Add type mapping to `KafkaConfig` in the producing service
3. Add type mapping to `KafkaConfig` in the consuming service(s)
4. Rebuild: `mvn clean install -pl nexus-common`

### Adding a New Service

1. Create module directory with `pom.xml` (parent: `nexus-parent`)
2. Add `nexus-common` dependency
3. Add `@SpringBootApplication(scanBasePackages = {"com.nexus.yourservice", "com.nexus.common"})`
4. Register module in parent `pom.xml` `<modules>`
5. Add Dockerfile, docker-compose entry, Prometheus scrape config
6. Add database schema to `infra/init-schemas.sql` and CI workflow

## API Documentation

Each service exposes Swagger UI at `/swagger-ui.html` when running.
See [docs/API.md](docs/API.md) for the full reference.

## Architecture Decision Records

Significant design decisions are documented in [docs/adr/](docs/adr/).
When making architectural changes, create a new ADR following the
existing format (Status, Context, Decision, Rationale, Trade-offs).

## Code Style

- Java: follow existing patterns (records for DTOs/events, Lombok for entities)
- React: functional components, TypeScript strict mode, Zustand for state
- Favor immutability: `List.of()`, records, `@Builder`
- Explicit over implicit: all Kafka configs in Java, not YAML
