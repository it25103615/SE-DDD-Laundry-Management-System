package _6.Y2.S1.MTR._6.LaundryLink.support;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SupportCaseWorkflowTest {
  @Test
  void preservesEveryStaffTransitionIncludingSameStatusUpdates() {
    // An independent matrix locks down the pre-refactor business rules for all 36 pairs.
    Map<String, Set<String>> expected = Map.of(
        "New", Set.of("New", "Assigned", "In Review"),
        "Assigned", Set.of("Assigned", "In Review"),
        "In Review", Set.of("In Review", "Resolved"),
        "Resolved", Set.of("Resolved", "Closed", "Reopened"),
        "Closed", Set.of("Closed", "Reopened"),
        "Reopened", Set.of("Reopened", "Assigned", "In Review"));
    expected.forEach((current, permitted) -> {
      var workflow = SupportCaseWorkflow.forStatus(current);
      expected.keySet().forEach(target ->
          assertEquals(permitted.contains(target), workflow.canTransitionTo(target),
              current + " -> " + target));
      assertFalse(workflow.canTransitionTo("Invalid"));
      assertFalse(workflow.canTransitionTo(null));
    });
  }

  @Test
  void preservesPermissionsAndCustomerConfirmationForEveryState() {
    for (String status : Set.of("New", "Assigned", "In Review", "Resolved", "Closed", "Reopened")) {
      var workflow = SupportCaseWorkflow.forStatus(status);
      assertEquals("New".equals(status), workflow.canEditOrDelete(), status);
      assertEquals(!"New".equals(status), workflow.requiresAssignee(), status);
      assertEquals(Set.of("Resolved", "Closed").contains(status), workflow.requiresResolutionNote(), status);
      assertEquals(!"Closed".equals(status), workflow.canMessage(), status);
      assertEquals(!"Closed".equals(status), workflow.canConfirmSatisfaction(), status);
      if (workflow.canConfirmSatisfaction()) {
        assertEquals("Closed", workflow.satisfactionStatus(true), status);
        assertEquals("Resolved".equals(status) ? "Reopened" : "In Review",
            workflow.satisfactionStatus(false), status);
      }
    }
  }

  @Test
  void legacyStatusCannotAdvanceAndContextsDoNotShareMutableCaseState() {
    var legacy = SupportCaseWorkflow.forStatus("Legacy");
    assertTrue(legacy.canTransitionTo("Legacy"));
    assertFalse(legacy.canTransitionTo("Assigned"));
    assertFalse(legacy.canEditOrDelete());
    assertTrue(legacy.canMessage());
    var closed = SupportCaseWorkflow.forStatus("Closed");
    var fresh = SupportCaseWorkflow.forStatus("New");
    assertFalse(closed.canMessage());
    assertTrue(fresh.canMessage());
    assertFalse(closed.canEditOrDelete());
    assertTrue(fresh.canEditOrDelete());
  }
}
