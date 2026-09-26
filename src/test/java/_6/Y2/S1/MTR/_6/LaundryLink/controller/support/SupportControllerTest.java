package _6.Y2.S1.MTR._6.LaundryLink.controller.support;

import _6.Y2.S1.MTR._6.LaundryLink.exception.support.SupportErrors;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.ReportService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.AdministrationService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.SupportService;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportControllerTest {
    MockMvc mvc;
    SupportService service;
    SupportAccess access;
    @BeforeEach void setup() {
        access=mock(SupportAccess.class);service=mock(SupportService.class);
        when(access.actor(isNull(),eq(1))).thenReturn(new SupportAccess.Actor(1,"Customer","CUSTOMER",true));
        mvc=MockMvcBuilders.standaloneSetup(new SupportController(access,service,mock(ReportService.class),mock(AdministrationService.class)))
            .setControllerAdvice(new SupportErrors()).build();
    }
    @Test void validCreateReturnsCreated() throws Exception {
        when(service.create(any(),any())).thenReturn(Map.of("id",42));
        mvc.perform(post("/api/support/cases").header("X-Demo-User","1").contentType("application/json")
            .content("{\"type\":\"Complaint\",\"subject\":\"Missing button\",\"message\":\"Please help with my shirt\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(42));
    }
    @Test void rejectsBlankCreateBeforeBusinessLogic() throws Exception {
        mvc.perform(post("/api/support/cases").header("X-Demo-User","1").contentType("application/json")
            .content("{\"type\":\"Complaint\",\"subject\":\" \",\"message\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        verifyNoInteractions(service);
    }
    @Test void rejectsOverlongMessage() throws Exception {
        mvc.perform(post("/api/support/cases").header("X-Demo-User","1").contentType("application/json")
            .content("{\"type\":\"Question\",\"subject\":\"Help\",\"message\":\""+"a".repeat(501)+"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void staffUpdateRequiresVersionAndNote() throws Exception {
        mvc.perform(patch("/api/support/cases/1").contentType("application/json")
            .content("{\"status\":\"In Review\",\"priority\":\"Normal\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void deleteRequiresVersion() throws Exception {
        mvc.perform(delete("/api/support/cases/1").header("X-Demo-User","1"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void deleteReturnsNoContent() throws Exception {
        mvc.perform(delete("/api/support/cases/1?version=0").header("X-Demo-User","1"))
            .andExpect(status().isNoContent());
        verify(service).delete(any(),eq(1),eq(0));
    }
}
