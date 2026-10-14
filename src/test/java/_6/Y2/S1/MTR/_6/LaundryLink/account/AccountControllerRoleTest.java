package _6.Y2.S1.MTR._6.LaundryLink.account;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountControllerRoleTest {
    @ParameterizedTest
    @CsvSource({"MANAGER,STAFF,201", "MANAGER,RIDER,201", "MANAGER,CSM,201", "MANAGER,MANAGER,403", "MANAGER,OWNER,403", "MANAGER,ADMIN,403", "OWNER,STAFF,201", "OWNER,RIDER,201", "OWNER,CSM,201", "OWNER,MANAGER,201", "ADMIN,MANAGER,403"})
    void directCreationChecksRole(String actorRole, String role, int expected) throws Exception {
        var service = mock(AccountService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
        var actor = new UsernamePasswordAuthenticationToken("actor@example.com", "unused", AuthorityUtils.createAuthorityList("ROLE_" + actorRole));
        mvc.perform(post("/api/admin/accounts").principal(actor).contentType("application/json").content(body(role)))
            .andExpect(status().is(expected));
        if (expected == 201) verify(service).createManagedUser(any());
        else verifyNoInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"MANAGER,MANAGER", "MANAGER,OWNER", "OWNER,MANAGER"})
    void legacyUpdateCannotBypassRoleEndpoint(String actorRole, String role) throws Exception {
        var service = mock(AccountService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AccountController(service)).build();
        var actor = new UsernamePasswordAuthenticationToken("actor@example.com", "unused", AuthorityUtils.createAuthorityList("ROLE_" + actorRole));
        mvc.perform(put("/api/admin/accounts/11").principal(actor).contentType("application/json").content(body(role)))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private String body(String role) {
        return """
            {"firstName":"New","lastName":"Employee","email":"employee@example.com",
             "phoneNumber":"0771234567","type":"%s","password":"Password1!"}
            """.formatted(role);
    }
}
