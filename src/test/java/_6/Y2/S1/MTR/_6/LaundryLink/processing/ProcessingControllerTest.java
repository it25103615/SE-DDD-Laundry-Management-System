package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.common.ApiException;
import _6.Y2.S1.MTR._6.LaundryLink.common.ApiExceptionHandler;
import _6.Y2.S1.MTR._6.LaundryLink.processing.dto.ReceiveResult;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for ProcessingController: request validation, HTTP status codes and the JSON
 * error body, with the services mocked (same standalone MockMvc style as SupportControllerTest).
 */
class ProcessingControllerTest {

    MockMvc mvc;
    ProcessingService processing;
    ProcessingIssueService issues;
    final Principal staffLogin = () -> "sam@staff.com";

    @BeforeEach
    void setup() {
        processing = mock(ProcessingService.class);
        issues = mock(ProcessingIssueService.class);
        when(processing.currentStaff(any())).thenReturn(new StaffMember(11, "Sam Staff", "STAFF"));
        mvc = MockMvcBuilders.standaloneSetup(new ProcessingController(processing, issues))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    /** TC-LP03: 0 and -1 are rejected with 400 before the service is called. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void receiveRejectsZeroAndNegative(int quantity) throws Exception {
        mvc.perform(post("/api/processing/orders/6/receive").principal(staffLogin).contentType("application/json")
                        .content("{\"lines\":[{\"orderLineID\":101,\"receivedQuantity\":" + quantity + "}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lines[0].receivedQuantity must be at least 1"));
        verify(processing, never()).receiveItems(anyInt(), any(), any());
    }

    /** TC-LP02 / LP04: a count mismatch is 422 with the mismatched lines. */
    @Test
    void mismatchReturns422WithTheLines() throws Exception {
        when(processing.receiveItems(eq(6), any(), any())).thenReturn(new ReceiveResult(false, 7, "In Shop",
                "Received quantities do not match the order.", List.of(new ReceiveResult.Mismatch(101, "Shirt / Blouse", 4, 3))));

        mvc.perform(post("/api/processing/orders/6/receive").principal(staffLogin).contentType("application/json")
                        .content("{\"lines\":[{\"orderLineID\":101,\"receivedQuantity\":3}]}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.mismatches[0].ordered").value(4))
                .andExpect(jsonPath("$.mismatches[0].received").value(3));
    }

    /** TC-LP08: an invalid transition surfaces as 409 with the service's message. */
    @Test
    void invalidTransitionIs409() throws Exception {
        when(processing.changeStatus(6, 12)).thenThrow(new ApiException(HttpStatus.CONFLICT, "Order #6 cannot move from Washing to Awaiting Delivery."));

        mvc.perform(put("/api/processing/orders/6/status").principal(staffLogin).contentType("application/json")
                        .content("{\"statusID\":12}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order #6 cannot move from Washing to Awaiting Delivery."));
    }

    @Test
    void qualityCheckResultMustBePassedOrFailed() throws Exception {
        mvc.perform(post("/api/processing/orders/6/quality-check").principal(staffLogin).contentType("application/json")
                        .content("{\"result\":\"Maybe\"}"))
                .andExpect(status().isBadRequest());
        verify(processing, never()).recordQualityCheck(anyInt(), any(), any());
    }

    @Test
    void issueTypeMustBeOneOfTheListedTypes() throws Exception {
        mvc.perform(post("/api/processing/issues").principal(staffLogin).contentType("application/json")
                        .content("{\"orderID\":6,\"issueType\":\"Lost button\",\"description\":\"Button missing\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(issues);
    }

    @Test
    void issueFormPermissionIsStaffOnly() throws Exception {
        mvc.perform(get("/api/processing/issues/permissions").principal(staffLogin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReport").value(true));
        when(processing.currentStaff(any())).thenReturn(new StaffMember(12,"Maya Manager","MANAGER"));
        mvc.perform(get("/api/processing/issues/permissions").principal(staffLogin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReport").value(false));
    }
}
