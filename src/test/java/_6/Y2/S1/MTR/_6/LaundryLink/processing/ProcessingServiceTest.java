package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.CheckRow;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.LineRow;
import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingRepository.OrderHeader;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.QualityCheckRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveItemsRequest;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessingService with mocked repositories, one or more per test case
 * (TC-LP01 to LP10). "The procedure" below means ProcessingRepository.updateStatus, which calls
 * dbo.sp_UpdateProcessingStatus and writes the status log row.
 */
class ProcessingServiceTest {

    static final int ORDER = 6;
    static final int IRONING_SERVICE = 2;
    static final int WASH_SERVICE = 1;
    static final int DRY_CLEANING_SERVICE = 3;

    ProcessingRepository repository;
    ProcessingIssueRepository issueRepository;
    ProcessingService service;
    final StaffMember staff = new StaffMember(11, "Sam Staff", "STAFF");

    @BeforeEach
    void setup() {
        repository = mock(ProcessingRepository.class);
        issueRepository = mock(ProcessingIssueRepository.class);
        service = new ProcessingService(repository, issueRepository);
        when(repository.findServiceIdByName("Dry Cleaning")).thenReturn(Optional.of(DRY_CLEANING_SERVICE));
        when(repository.findStatusLabels()).thenReturn(Map.of(7, "In Shop", 8, "Verifying Items", 9, "Washing",
                10, "Drying", 11, "Ironing", 12, "Awaiting Delivery", 19, "Dry Clean"));
    }

    /** Puts order #6 at the given status. */
    void orderAt(int statusID, String label) {
        when(repository.findOrder(ORDER)).thenReturn(Optional.of(
                new OrderHeader(ORDER, statusID, label, 20, "Priya Fernando", "Ring bell twice")));
    }

    /** The TC-LP01 order: Shirt / Blouse x4 and Trousers / Skirt x2 (wash route). */
    void shirtsAndTrousers() {
        when(repository.findLines(ORDER)).thenReturn(List.of(
                new LineRow(101, "Shirt / Blouse", IRONING_SERVICE, "Ironing", 4, null, null),
                new LineRow(102, "Trousers / Skirt", IRONING_SERVICE, "Ironing", 2, null, null)));
    }

    void singleLine(int serviceID) {
        when(repository.findLines(ORDER)).thenReturn(List.of(
                new LineRow(201, "Shirt / Blouse", serviceID, "Service", 3, null, null)));
    }

    static ReceiveItemsRequest counts(int shirts, int trousers) {
        return new ReceiveItemsRequest(List.of(
                new ReceiveItemsRequest.Line(101, shirts, "As expected"),
                new ReceiveItemsRequest.Line(102, trousers, null)));
    }

    static CheckRow check(String result, boolean packed) {
        return new CheckRow(1, result, null, null, packed, null, "Sam Staff", LocalDateTime.now());
    }

    static HttpStatus statusOf(ApiException exception) {
        return exception.status;
    }

    // ---------------------------------------------------------------- receiving (LP01 - LP04)

    @Test
    void receiveMatchingCountsMovesToVerifying() {                       // TC-LP01
        orderAt(7, "In Shop");
        shirtsAndTrousers();

        ReceiveResult result = service.receiveItems(ORDER, counts(4, 2), staff);

        assertTrue(result.accepted());
        assertEquals(8, result.statusID());
        verify(repository).saveReceivedItem(101, 4, "As expected", 11);
        verify(repository).saveReceivedItem(102, 2, "As expected", 11);   // missing condition defaults
        verify(repository).updateStatus(ORDER, 8);
    }

    @Test
    void receiveFewerThanOrderedReturnsMismatchAndSavesNothing() {       // TC-LP02
        orderAt(7, "In Shop");
        shirtsAndTrousers();

        ReceiveResult result = service.receiveItems(ORDER, counts(3, 2), staff);

        assertFalse(result.accepted());
        assertEquals(List.of(new ReceiveResult.Mismatch(101, "Shirt / Blouse", 4, 3)), result.mismatches());
        verify(repository, never()).saveReceivedItem(anyInt(), anyInt(), any(), anyInt());
        verify(repository, never()).updateStatus(anyInt(), anyInt());
    }

    @Test
    void receiveMoreThanOrderedReturnsMismatchAndKeepsStatus() {         // TC-LP04
        orderAt(7, "In Shop");
        shirtsAndTrousers();

        ReceiveResult result = service.receiveItems(ORDER, counts(4, 3), staff);

        assertFalse(result.accepted());
        assertEquals(7, result.statusID());
        assertEquals(List.of(new ReceiveResult.Mismatch(102, "Trousers / Skirt", 2, 3)), result.mismatches());
        verify(repository, never()).updateStatus(anyInt(), anyInt());
    }

    @Test
    void receiveRequiresEveryLine() {
        orderAt(7, "In Shop");
        shirtsAndTrousers();
        var onlyShirts = new ReceiveItemsRequest(List.of(new ReceiveItemsRequest.Line(101, 4, null)));

        var error = assertThrows(ApiException.class, () -> service.receiveItems(ORDER, onlyShirts, staff));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(error));
    }

    @Test
    void receiveOnlyWhileInShop() {
        orderAt(8, "Verifying Items");
        shirtsAndTrousers();

        var error = assertThrows(ApiException.class, () -> service.receiveItems(ORDER, counts(4, 2), staff));
        assertEquals(HttpStatus.CONFLICT, statusOf(error));
        verify(repository, never()).saveReceivedItem(anyInt(), anyInt(), any(), anyInt());
    }

    // ---------------------------------------------------------------- status changes (LP07, LP08)

    @Test
    void washingToDryingCallsTheProcedure() {                           // TC-LP07
        orderAt(9, "Washing");
        singleLine(WASH_SERVICE);

        service.changeStatus(ORDER, 10);

        verify(repository).updateStatus(ORDER, 10);
    }

    @Test
    void skippingAStageIsRejectedAndNeverLogged() {                     // TC-LP08
        orderAt(9, "Washing");
        singleLine(WASH_SERVICE);

        var error = assertThrows(ApiException.class, () -> service.changeStatus(ORDER, 12));

        assertEquals(HttpStatus.CONFLICT, statusOf(error));
        assertTrue(error.getMessage().contains("The next stage is Drying"));
        verify(repository, never()).updateStatus(anyInt(), anyInt());
    }

    @Test
    void verifyingRoutesByService() {
        orderAt(8, "Verifying Items");
        singleLine(DRY_CLEANING_SERVICE);

        // Dry cleaning lines: Washing is refused, Dry Clean is allowed.
        assertThrows(ApiException.class, () -> service.changeStatus(ORDER, 9));
        service.changeStatus(ORDER, 19);

        verify(repository, never()).updateStatus(ORDER, 9);
        verify(repository).updateStatus(ORDER, 19);
    }

    @Test
    void dryCleanGoesStraightToIroning() {
        orderAt(19, "Dry Clean");
        singleLine(DRY_CLEANING_SERVICE);

        assertThrows(ApiException.class, () -> service.changeStatus(ORDER, 10));
        service.changeStatus(ORDER, 11);

        verify(repository, never()).updateStatus(ORDER, 10);
        verify(repository).updateStatus(ORDER, 11);
    }

    // ---------------------------------------------------------------- quality check (LP09)

    @Test
    void failedCheckSendsOrderBackToReworkStage() {                     // TC-LP09
        orderAt(11, "Ironing");
        singleLine(WASH_SERVICE);

        service.recordQualityCheck(ORDER, new QualityCheckRequest("Failed", 9, null, "Stain remains"), staff);

        verify(repository).insertQualityCheck(ORDER, "Failed", 9, false, "Stain remains", 11);
        verify(repository).updateStatus(ORDER, 9);
    }

    @Test
    void reworkAtIroningOnlyRecordsTheCheck() {
        orderAt(11, "Ironing");
        singleLine(WASH_SERVICE);

        service.recordQualityCheck(ORDER, new QualityCheckRequest("Failed", 11, null, null), staff);

        verify(repository).insertQualityCheck(ORDER, "Failed", 11, false, null, 11);
        verify(repository, never()).updateStatus(anyInt(), anyInt());
    }

    @Test
    void reworkStageMustBeOnTheRoute() {
        orderAt(11, "Ironing");
        singleLine(DRY_CLEANING_SERVICE);

        var error = assertThrows(ApiException.class,
                () -> service.recordQualityCheck(ORDER, new QualityCheckRequest("Failed", 9, null, null), staff));

        assertEquals(HttpStatus.BAD_REQUEST, statusOf(error));
        verify(repository, never()).insertQualityCheck(anyInt(), any(), any(), anyBoolean(), any(), anyInt());
    }

    @Test
    void qualityCheckOnlyAfterIroning() {
        orderAt(10, "Drying");
        singleLine(WASH_SERVICE);

        var error = assertThrows(ApiException.class,
                () -> service.recordQualityCheck(ORDER, new QualityCheckRequest("Passed", null, true, null), staff));
        assertEquals(HttpStatus.CONFLICT, statusOf(error));
    }

    // ---------------------------------------------------------------- packing and ready (LP10)

    @Test
    void readyRequiresPassedAndPacked() {                               // TC-LP10
        orderAt(11, "Ironing");
        singleLine(WASH_SERVICE);

        // No check yet, then a passed but unpacked check: both refused.
        when(repository.findLatestQualityCheck(ORDER)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.markReady(ORDER));
        when(repository.findLatestQualityCheck(ORDER)).thenReturn(Optional.of(check("Passed", false)));
        assertThrows(ApiException.class, () -> service.markReady(ORDER));
        verify(repository, never()).updateStatus(anyInt(), anyInt());

        // Passed and packed: Ironing -> Awaiting Delivery.
        when(repository.findLatestQualityCheck(ORDER)).thenReturn(Optional.of(check("Passed", true)));
        service.markReady(ORDER);
        verify(repository).updateStatus(ORDER, 12);
    }

    @Test
    void packOnlyAfterAPassedCheck() {
        orderAt(11, "Ironing");
        singleLine(WASH_SERVICE);
        when(repository.findLatestQualityCheck(ORDER)).thenReturn(Optional.of(check("Failed", false)));

        assertThrows(ApiException.class, () -> service.pack(ORDER));
        verify(repository, never()).markLatestPassedCheckPacked(anyInt());

        when(repository.findLatestQualityCheck(ORDER)).thenReturn(Optional.of(check("Passed", false)));
        service.pack(ORDER);
        verify(repository).markLatestPassedCheckPacked(ORDER);
    }

    // ---------------------------------------------------------------- access

    @Test
    void onlyStaffRolesMayProcess() {
        when(repository.findActiveUserByEmail("anna@customer.com"))
                .thenReturn(Optional.of(new StaffMember(9, "Anna Customer", "CUSTOMER")));

        assertEquals(HttpStatus.UNAUTHORIZED, statusOf(assertThrows(ApiException.class, () -> service.currentStaff(null))));
        assertEquals(HttpStatus.FORBIDDEN,
                statusOf(assertThrows(ApiException.class, () -> service.currentStaff(() -> "anna@customer.com"))));
    }
}
