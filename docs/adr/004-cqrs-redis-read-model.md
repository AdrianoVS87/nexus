# ADR-004: CQRS with Redis Read Model for Inventory

## Status
Accepted

## Context
The product catalog has a read-heavy access pattern: browsing products
is orders of magnitude more frequent than stock reservation writes.
Serving catalog reads from PostgreSQL under load would contend with
write transactions (optimistic locking, stock decrements).

## Decision
Implement **CQRS** in the Inventory Service with PostgreSQL as the
write model and Redis as the read model for product catalog queries.

## Implementation
- **Write path**: stock reservations go through PostgreSQL with
  @Version optimistic locking. On successful write, the updated
  product is synced to Redis.
- **Read path**: getAllProducts() and getProductById() read from Redis
  first. On cache miss, fall back to PostgreSQL and populate cache.
- **Cache invalidation**: explicit invalidation on stock changes
  (reservation, release). TTL of 30 minutes as safety net.
- **Startup sync**: all products are synced to Redis on application
  startup via @EventListener(ApplicationReadyEvent).

## Trade-offs
- **Eventual consistency**: a brief window exists where Redis shows
  stale stock after a reservation. Acceptable for catalog display;
  actual stock validation happens at reservation time in PostgreSQL.
- **Redis dependency**: if Redis is down, reads fall back to PostgreSQL
  gracefully (try/catch in every Redis call). The service degrades
  but doesn't fail.

## Why Not Event Sourcing
Event sourcing would provide a complete audit trail of stock changes
but adds significant complexity (event store, projections, snapshots).
The current approach achieves the performance goal with simpler
operational characteristics.
