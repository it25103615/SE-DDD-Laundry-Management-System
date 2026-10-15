package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * State pattern context for the customer support major component.
 * The database keeps its existing status strings; this context selects the matching state object
 * and delegates workflow decisions to it. SupportService still owns authorization, transactions,
 * persistence and notifications. A new context is created for each stored case snapshot, so no
 * mutable state is shared between requests or customers.
 */
final class SupportCaseWorkflow {
  private static final Map<String, SupportCaseState> STATES = Map.of(
      "New", new NewState(),
      "Assigned", new AssignedState(),
      "In Review", new InReviewState(),
      "Resolved", new ResolvedState(),
      "Closed", new ClosedState(),
      "Reopened", new ReopenedState());

  private final SupportCaseState state;

  private SupportCaseWorkflow(SupportCaseState state) {
    this.state = state;
  }

  static SupportCaseWorkflow forStatus(String status) {
    // Legacy/unrecognised values retain the old same-status-only transition behaviour.
    SupportCaseState selected = status == null ? null : STATES.get(status);
    return new SupportCaseWorkflow(selected == null ? new UnknownState(status) : selected);
  }

  boolean canTransitionTo(String target) { return state.canTransitionTo(target); }
  boolean canEditOrDelete() { return state.canEditOrDelete(); }
  boolean canMessage() { return state.canMessage(); }
  boolean canConfirmSatisfaction() { return state.canConfirmSatisfaction(); }
  boolean requiresResolutionNote() { return state.requiresResolutionNote(); }
  boolean requiresAssignee() { return state.requiresAssignee(); }
  String satisfactionStatus(boolean satisfied) { return state.satisfactionStatus(satisfied); }

  // Common same-status updates allow priority/assignment changes without advancing the case.
  private abstract static class BaseState implements SupportCaseState {
    private final String name;
    private final Set<String> next;

    BaseState(String name, String... next) {
      this.name = name;
      this.next = Set.of(next);
    }

    @Override public String name() { return name; }

    @Override public boolean canTransitionTo(String target) {
      return Objects.equals(name, target) || (target != null && next.contains(target));
    }
  }

  /** Only a new request can be edited/deleted, and it may remain unassigned. */
  private static final class NewState extends BaseState {
    NewState() { super("New", "Assigned", "In Review"); }
    @Override public boolean canEditOrDelete() { return true; }
    @Override public boolean requiresAssignee() { return false; }
  }

  private static final class AssignedState extends BaseState {
    AssignedState() { super("Assigned", "In Review"); }
  }

  private static final class InReviewState extends BaseState {
    InReviewState() { super("In Review", "Resolved"); }
  }

  /** An unsatisfied customer reopens a resolved case instead of restarting review directly. */
  private static final class ResolvedState extends BaseState {
    ResolvedState() { super("Resolved", "Closed", "Reopened"); }
    @Override public boolean requiresResolutionNote() { return true; }
    @Override public String satisfactionStatus(boolean satisfied) {
      return satisfied ? "Closed" : "Reopened";
    }
  }

  /** Closed cases must be reopened before messaging or customer confirmation. */
  private static final class ClosedState extends BaseState {
    ClosedState() { super("Closed", "Reopened"); }
    @Override public boolean canMessage() { return false; }
    @Override public boolean canConfirmSatisfaction() { return false; }
    @Override public boolean requiresResolutionNote() { return true; }
  }

  private static final class ReopenedState extends BaseState {
    ReopenedState() { super("Reopened", "Assigned", "In Review"); }
  }

  private static final class UnknownState extends BaseState {
    UnknownState(String name) { super(name); }
  }
}
