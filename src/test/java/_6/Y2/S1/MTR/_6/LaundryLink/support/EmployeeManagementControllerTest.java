package _6.Y2.S1.MTR._6.LaundryLink.support;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmployeeManagementControllerTest {
    MockMvc mvc;
    AdministrationService service;
    SupportAccess access;
    SupportAccess.Actor owner=new SupportAccess.Actor(1,"Owner","OWNER",false);
    @BeforeEach void setup() {
        service=mock(AdministrationService.class); access=mock(SupportAccess.class);
        when(access.actor(any(),isNull())).thenReturn(owner);
        mvc=MockMvcBuilders.standaloneSetup(new SupportController(access,mock(SupportService.class),mock(ReportService.class),service))
            .setControllerAdvice(new SupportErrors()).build();
    }
    @Test void validRoleRequestUsesDedicatedAction() throws Exception {
        mvc.perform(patch("/api/support/administration/staff/10/role").contentType("application/json")
            .content("{\"role\":\"RIDER\",\"version\":3}")) .andExpect(status().isNoContent());
        verify(service).changeStaffRole(owner,10,"RIDER",3);
    }
    @Test void invalidRoleAndMissingVersionAreRejectedBeforeService() throws Exception {
        for(String body:new String[]{"{\"role\":\"OWNER\",\"version\":3}","{\"role\":\"RIDER\"}","{\"role\":\"STAFF\",\"version\":-1}"})
            mvc.perform(patch("/api/support/administration/staff/10/role").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void deletionRequiresVersion() throws Exception {
        mvc.perform(delete("/api/support/administration/staff/10").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void validDeletionUsesDedicatedAction() throws Exception {
        mvc.perform(delete("/api/support/administration/staff/10").contentType("application/json").content("{\"version\":3}"))
            .andExpect(status().isNoContent());
        verify(service).deleteStaff(owner,10,3);
    }
    @Test void anonymousUserCannotReachEmployeeActions() throws Exception {
        when(access.actor(any(),isNull())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in first."));
        mvc.perform(delete("/api/support/administration/staff/10").contentType("application/json").content("{\"version\":3}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
