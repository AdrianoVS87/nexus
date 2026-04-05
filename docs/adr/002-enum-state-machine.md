# ADR-002: Enum-Based State Machine over Framework

## Status
Accepted

## Context
The saga orchestrator needs a state machine to enforce valid order
status transitions and prevent corruption from event replays.

Options considered:
- Spring State Machine
- Custom enum with transition validation
- State pattern (GoF) with polymorphic classes

## Decision
We chose a **custom enum-based state machine** where each OrderStatus
constant declares its valid outgoing transitions via `canTransitionTo()`.

## Rationale
1. **Zero dependencies**: no framework to learn, configure, or debug.
   The entire state machine is ~50 lines in one enum file.

2. **Compile-time safety**: transitions are defined as enum constants,
   so typos are caught at compile time. Spring State Machine uses
   string-based state IDs.

3. **Testability**: parameterized tests cover all valid and invalid
   transitions with a simple CSV source. No Spring context needed.

4. **Domain encapsulation**: Order.transitionTo() is the single mutation
   point. No external code can set an invalid status because the setter
   is removed — only the validated transition method exists.

## Trade-offs
- No built-in persistence of state machine history. Acceptable because
  we persist order status in PostgreSQL and log transitions.
- No visual state machine diagram generation. Mitigated by the Mermaid
  diagram in README.

## Alternatives Rejected
- **Spring State Machine**: too heavyweight for 7 states. Adds config
  complexity, runtime overhead, and a learning curve disproportionate
  to the problem size.
- **GoF State pattern**: creates 7 classes for 7 states. More code,
  more indirection, no practical benefit over an enum at this scale.
