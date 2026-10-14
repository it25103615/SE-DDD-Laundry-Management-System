# State pattern: customer support cases

This pattern belongs to the Reporting, Administration and Customer Support major component.
It encapsulates case lifecycle rules while retaining the existing API and database status strings.

## Participants

- `SupportCaseState`: interface for state-dependent actions and transition rules.
- `SupportCaseWorkflow`: context which selects the state for a stored case and delegates decisions.
- `NewState`, `AssignedState`, `InReviewState`, `ResolvedState`, `ClosedState`, `ReopenedState`:
  concrete state classes inside the context. They are immutable and safe to reuse across requests.
- `SupportService`: loads the case, asks the context whether an action is allowed, and persists
  changes with the existing transaction/version checks. The next request selects the new state
  from the saved status; the context does not write to the database itself.

## Preserved staff transitions

| Current state | Next states |
| --- | --- |
| New | Assigned, In Review |
| Assigned | In Review |
| In Review | Resolved |
| Resolved | Closed, Reopened |
| Closed | Reopened |
| Reopened | Assigned, In Review |

Same-status updates remain allowed for priority/assignment changes. Only New cases can be edited
or deleted. Closed cases reject messages and satisfaction confirmation. Resolved and Closed
destinations require resolution notes; all destinations except New require an assignee.

Customer satisfaction remains a separate action: after an eligible question/complaint receives
a staff reply, satisfaction closes it; dissatisfaction reopens a Resolved case and sends other
open states to In Review. Feedback retains its separate one-time reply rules and bypasses the
closed-case messaging restriction as before.

Authorization, assignee validation, audit history, notification delivery and optimistic locking
remain in SupportService. No database migration or frontend change is required.

## Verification

`SupportCaseWorkflowTest` covers all 36 staff transition pairs, same-status updates, action
permissions, customer satisfaction outcomes and legacy status handling. Existing service,
controller and database integration tests exercise the context through the real support workflow.

Validated on 14 October 2026 with the full Maven regression suite and local SQL Server:
442 tests passed, with zero failures, errors or skipped tests. This includes the three new
State pattern regression tests and existing support assignment integration tests.
