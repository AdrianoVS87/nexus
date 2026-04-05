# ADR-001: Orchestration-Based Saga over Choreography

## Status
Accepted

## Context
The Nexus platform requires distributed transaction management across
Order, Payment, and Inventory services. Two main patterns exist:

- **Choreography**: each service publishes events and reacts to others.
  No central coordinator. Flow is implicit in the event subscriptions.
- **Orchestration**: a central orchestrator (Order Service) drives each
  step sequentially and handles compensation on failure.

## Decision
We chose **orchestration-based saga** with the Order Service as the
central orchestrator.

## Rationale
1. **Explicit flow**: the saga state machine (OrderStatus enum) makes
   the entire transaction flow visible in one file. Debugging a failed
   order means reading one class, not tracing events across 4 services.

2. **Compensation clarity**: rollback logic lives in the orchestrator.
   Adding a new compensation step (e.g., refund on inventory failure)
   is a single method addition, not a cross-service change.

3. **State guards**: the orchestrator validates expected pre-condition
   status before each transition, making the saga idempotent and
   replay-safe — critical for Kafka consumer rebalances.

4. **Bounded complexity**: with 4 services and 3 saga steps, choreography
   would create a web of event subscriptions that's harder to reason
   about than a linear orchestrator.

## Trade-offs
- Single point of failure: if Order Service is down, no sagas progress.
  Mitigated by Kafka persistence (events wait until service recovers).
- Coupling: Order Service knows about all saga participants. Acceptable
  at this scale; would revisit with 10+ services.

## Alternatives Considered
- **Choreography**: rejected for readability and debugging reasons above.
- **Axon Framework / Eventuate Tram**: rejected to avoid framework lock-in
  and keep the implementation transparent for learning purposes.
