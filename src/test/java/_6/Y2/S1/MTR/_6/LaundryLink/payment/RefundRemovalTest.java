package _6.Y2.S1.MTR._6.LaundryLink.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundRemovalTest {
    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "MANAGER", "OWNER", "ADMIN", "STAFF", "CSM"})
    void retiredRoutesHaveNoHandlerEvenWithoutSecurityFilters(String role) throws Exception {
        var service = mock(PaymentService.class);
        var access = mock(PaymentAccessService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service, access)).build();
        var principal = new UsernamePasswordAuthenticationToken("user", "unused",
                AuthorityUtils.createAuthorityList("ROLE_" + role));
        for (String path : new String[]{
                "/api/payments/4/refund-request",
                "/api/payments/management/4/refund/approve",
                "/api/payments/management/4/refund/reject"}) {
            mvc.perform(post(path).principal(principal).contentType("application/json")
                    .content("{\"reason\":\"Old client request\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(get(path).principal(principal)).andExpect(status().isNotFound());
        }
        verifyNoInteractions(service, access);
    }
}
