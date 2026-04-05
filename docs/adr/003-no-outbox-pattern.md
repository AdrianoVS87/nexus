# ADR-003: Deferred Outbox Pattern Implementation

## Status
Accepted (with planned evolution)

## Context
The current implementation uses a "publish after commit" approach:
1. Save entity to PostgreSQL within @Transactional
2. Publish event to Kafka via async send with error callback

This creates a dual-write risk: if Kafka is unreachable after the
database commits, the event is lost (or vice versa).

The Transactional Outbox pattern solves this by writing events to an
outbox table within the same database transaction, then using a
separate process (polling publisher or CDC via Debezium) to relay
events to Kafka.

## Decision
We chose to **defer the outbox pattern** and mitigate the dual-write
risk with Kafka producer configuration instead.

## Current Mitigations
1. **acks=all**: Kafka acknowledges only when all in-sync replicas
   have written the record, minimizing producer-side data loss.
2. **enable.idempotence=true**: exactly-once producer semantics
   prevent duplicate events on network retries.
3. **retries=3**: bounded retry with backoff on transient failures.
4. **Dead letter queues**: failed consumer processing routes to DLT
   topics for manual recovery.
5. **Saga state guards**: idempotent event handling prevents
   corruption from duplicate or replayed events.

## Rationale for Deferral
1. **Operational complexity**: Debezium CDC requires Kafka Connect,
   a connector configuration, and monitoring of the CDC pipeline.
   This triples the infrastructure footprint for a portfolio project.

2. **Polling publisher alternative**: simpler but adds latency and
   requires a scheduled job + outbox table per service.

3. **Risk assessment**: in the current single-node Kafka deployment,
   the window for dual-write inconsistency is extremely small. The
   mitigations above reduce it to near-zero for demo purposes.

## Planned Evolution
When the platform moves to multi-node Kafka or production deployment:
1. Add outbox table to each service's Flyway migrations
2. Implement polling publisher (simplest) or integrate Debezium (robust)
3. Remove direct Kafka sends from service code

## References
- Microservices Patterns, Ch. 3 (Chris Richardson)
- https://microservices.io/patterns/data/transactional-outbox.html
