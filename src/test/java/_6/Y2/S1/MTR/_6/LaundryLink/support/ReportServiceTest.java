package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    final SupportRepository repo = mock(SupportRepository.class);
    final ReportService service = new ReportService(repo, new SupportAccess(null));
    final SupportAccess.Actor owner = new SupportAccess.Actor(5, "Owner", "OWNER", false);

    @Test void reversedDatesAreRejectedBeforeDatabaseAccess() {
        var error = assertThrows(ResponseStatusException.class, () -> service.report(owner,
            LocalDate.parse("2026-09-30"), LocalDate.parse("2026-07-08"), null));
        assertEquals(400, error.getStatusCode().value());
        assertEquals("Start date must be on or before end date.", error.getReason());
        verifyNoInteractions(repo);
    }

    @ParameterizedTest
    @CsvSource({"2026-09-30,2026-09-30", "2026-07-08,2026-09-30", ",2026-09-30", "2026-09-30,", ","})
    void validAndOpenEndedRangesRemainSupported(String from, String to) {
        when(repo.query(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("totalOrders", 0)));
        LocalDate start = from == null ? null : LocalDate.parse(from);
        LocalDate end = to == null ? null : LocalDate.parse(to);
        var report = service.report(owner, start, end, null);
        assertEquals(Map.of("from", from == null ? "All time" : from,
            "to", to == null ? "All time" : to, "serviceId", "All services"), report.get("period"));
        verify(repo, times(4)).query(anyString(), eq(start), eq(start), eq(end), eq(end), isNull(), isNull());
    }
}
