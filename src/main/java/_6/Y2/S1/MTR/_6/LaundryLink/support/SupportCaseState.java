package _6.Y2.S1.MTR._6.LaundryLink.support;

/** State pattern contract: each case state owns the actions allowed at that stage. */
interface SupportCaseState {
  String name();

  boolean canTransitionTo(String target);

  default boolean canEditOrDelete() {
    return false;
  }

  default boolean canMessage() {
    return true;
  }

  default boolean canConfirmSatisfaction() {
    return true;
  }

  default boolean requiresResolutionNote() {
    return false;
  }

  default boolean requiresAssignee() {
    return true;
  }

  // Customer confirmation is a separate action from a staff status change.
  default String satisfactionStatus(boolean satisfied) {
    return satisfied ? "Closed" : "In Review";
  }
}
