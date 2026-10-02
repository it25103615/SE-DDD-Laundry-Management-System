package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.IssueRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ProcessingOrderDetail;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.QualityCheckRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveItemsRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the processing test cases against the real database, including the
 * dbo.sp_UpdateProcessingStatus procedure and the order-notification trigger.
 *
 * <p>Needs the sample data and migration 005 (the test orders of priya.fernando@...). Each test
 * runs in a transaction that is rolled back afterwards, so the test orders keep their starting
 * statuses. When the test orders are missing, the tests are skipped instead of failing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProcessingIntegrationTest {

    private static final String TEST_CUSTOMER = "priya.fernando@assignment.laundrylink.lk";

    @Autowired ProcessingService processing;
    @Autowired ProcessingIssueService issues;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;

    StaffMember staff;

    @BeforeEach
    void signInAsStaff() {
        staff = processing.currentStaff(() -> "sam@staff.com");
    }

    /** The newest test order of the processing customer at the given status, or skip the test. */
    int testOrderAt(int statusID) {
        List<Integer> ids = db.queryForList("""
                SELECT o.orderID FROM orders o JOIN users u ON u.userID = o.userID
                WHERE u.email = ? AND o.statusID = ? ORDER BY o.orderID DESC
                """, Integer.class, TEST_CUSTOMER, statusID);
        assumeTrue(!ids.isEmpty(), "Migration 005 test orders not found - run database/migrations/005_processing.sql");
        return ids.getFirst();
    }

    int logRows(int orderID, int before, int after) {
        return db.queryForObject("""
                SELECT COUNT(*) FROM logs WHERE orderID = ? AND status_before = ? AND status_after = ?
                  AND logDate = CONVERT(DATE, SYSDATETIME())
                """, Integer.class, orderID, before, after);
    }

    @Test
    void receivingInstructionsAndIssueReport() {
        int order = testOrderAt(ProcessingTransitions.IN_SHOP);
        ProcessingOrderDetail detail = processing.getOrder(order);
        assertEquals("Ring bell twice", detail.deliveryInstruction());                       // TC-LP05

        // TC-LP01: every line counted correctly -> Verifying Items, with a log row for today.
        int receivedLogs = logRows(order, 7, 8);
        var counts = new ReceiveItemsRequest(detail.lines().stream()
                .map(line -> new ReceiveItemsRequest.Line(line.orderLineID(), line.quantity(), "As expected")).toList());
        assertTrue(processing.receiveItems(order, counts, staff).accepted());
        assertEquals(ProcessingTransitions.VERIFYING_ITEMS, processing.getOrder(order).statusID());
        assertEquals(receivedLogs + 1, logRows(order, 7, 8));

        // TC-LP06: a damaged item opens a High-priority support case in the CSM queue, filed under
        // the order's customer, with the issue type as the case type and the item as the subject.
        int issuesBefore = detail.issues().size();
        int line = detail.lines().getFirst().orderLineID();
        var issue = issues.reportIssue(new IssueRequest(order, line, "Damaged item", "Torn sleeve"), staff);
        assertEquals("New", issue.caseStatus());
        var saved = db.queryForMap("""
                SELECT f.caseType, f.subject, f.priority, f.userID, o.userID AS customerID
                FROM feedback f JOIN orders o ON o.orderID = f.orderID WHERE f.feedbackID = ?
                """, issue.caseID());
        assertEquals("Damaged item", saved.get("caseType"));
        assertEquals(detail.lines().getFirst().itemName() + " (order #" + order + ")", saved.get("subject"));
        assertEquals("High", saved.get("priority"));
        assertEquals(saved.get("customerID"), saved.get("userID"));
        assertEquals(staff.name(), issue.reportedBy());
        assertEquals(issuesBefore + 1, processing.getOrder(order).issues().size());
    }

    @Test
    void cleaningStagesQualityCheckAndRelease() {
        int order = testOrderAt(ProcessingTransitions.WASHING);
        int washToDry = logRows(order, 9, 10), reworkLogs = logRows(order, 11, 9), releaseLogs = logRows(order, 11, 12);

        processing.changeStatus(order, ProcessingTransitions.DRYING);                         // TC-LP07
        assertEquals(washToDry + 1, logRows(order, 9, 10));

        int logsBefore = db.queryForObject("SELECT COUNT(*) FROM logs WHERE orderID = ?", Integer.class, order);
        assertThrows(ApiException.class, () -> processing.changeStatus(order, ProcessingTransitions.AWAITING_DELIVERY)); // TC-LP08
        assertEquals(logsBefore, db.queryForObject("SELECT COUNT(*) FROM logs WHERE orderID = ?", Integer.class, order));

        processing.changeStatus(order, ProcessingTransitions.IRONING);
        processing.recordQualityCheck(order, new QualityCheckRequest("Failed", 9, null, "Collar stain"), staff); // TC-LP09
        assertEquals(ProcessingTransitions.WASHING, processing.getOrder(order).statusID());
        assertEquals(reworkLogs + 1, logRows(order, 11, 9));

        // Back through the stages, then pass, pack and release (TC-LP10).
        processing.changeStatus(order, ProcessingTransitions.DRYING);
        processing.changeStatus(order, ProcessingTransitions.IRONING);
        processing.recordQualityCheck(order, new QualityCheckRequest("Passed", null, false, null), staff);
        assertThrows(ApiException.class, () -> processing.markReady(order));                  // not packed yet
        processing.pack(order);
        ProcessingOrderDetail released = processing.markReady(order);
        assertEquals(ProcessingTransitions.AWAITING_DELIVERY, released.statusID());
        assertEquals(releaseLogs + 1, logRows(order, 11, 12));
        // The database trigger tells the customer about the new status.
        assertTrue(db.queryForObject("""
                SELECT COUNT(*) FROM notifications n JOIN orders o ON o.userID = n.recipientID
                WHERE o.orderID = ? AND n.relatedID = ? AND n.message LIKE '%Awaiting Delivery%'
                """, Integer.class, order, order) > 0);
    }

    /**
     * The status history is written by the dbo.trg_order_status_log trigger (migration 008), not
     * by the code that changes the status. A plain UPDATE of orders.statusID, which is how the
     * rider module moves an order, must therefore leave exactly one log row behind.
     */
    @Test
    void aDirectStatusUpdateIsLoggedOnceByTheTrigger() {
        int order = testOrderAt(ProcessingTransitions.WASHING);
        String countLogs = "SELECT COUNT(*) FROM logs WHERE orderID = ?";
        int logsBefore = db.queryForObject(countLogs, Integer.class, order);
        int washToCompleted = logRows(order, 9, 15);

        // An update that leaves the status as it is writes nothing.
        db.update("UPDATE orders SET statusID = statusID WHERE orderID = ?", order);
        assertEquals(logsBefore, db.queryForObject(countLogs, Integer.class, order));

        // A real change writes one row holding the status before and after.
        assertEquals(1, db.update("UPDATE orders SET statusID = 15 WHERE orderID = ?", order));
        assertEquals(logsBefore + 1, db.queryForObject(countLogs, Integer.class, order));
        assertEquals(washToCompleted + 1, logRows(order, 9, 15));
    }

    @Test
    void staffPagesAndApiAreForStaffOnly() throws Exception {
        var staffLogin = mvc.perform(formLogin("/login").userParameter("email").user("sam@staff.com").password("Sam1234")).andReturn();
        var staffSession = (MockHttpSession) staffLogin.getRequest().getSession(false);
        mvc.perform(get("/html/staff/receive_items.html").session(staffSession)).andExpect(status().isOk());
        mvc.perform(get("/api/processing/orders").session(staffSession)).andExpect(status().isOk());

        var customerLogin = mvc.perform(formLogin("/login").userParameter("email").user("anna@customer.com").password("Anna1234")).andReturn();
        var customerSession = (MockHttpSession) customerLogin.getRequest().getSession(false);
        mvc.perform(get("/api/processing/orders").session(customerSession))
                .andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
    }
}
