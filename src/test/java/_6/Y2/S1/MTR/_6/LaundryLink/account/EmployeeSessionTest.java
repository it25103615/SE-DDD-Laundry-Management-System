package _6.Y2.S1.MTR._6.LaundryLink.account;

import java.util.List;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EmployeeSessionTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void changedRoleRevokesSessionBeforeControllerRuns() throws Exception {
        check("STAFF",true,false);
    }
    @Test void unchangedRoleKeepsSession() throws Exception { check("MANAGER",true,true); }
    @Test void deletedEmployeeSessionIsRejected() throws Exception { check("MANAGER",false,false); }
    void check(String role,boolean active,boolean allowed) throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.query(anyString(),any(RowMapper.class),eq(10))).thenReturn(active?List.of("employee@example.com"):List.of());
        when(db.queryForObject(anyString(),eq(String.class),eq(10))).thenReturn(role);
        var principal=new AccountSessionGuard.AccountPrincipal(10,"employee@example.com","hash",true,"MANAGER");
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        var request=new MockHttpServletRequest("GET","/api/support/administration/staff");
        var session=(MockHttpSession)request.getSession();
        var response=new MockHttpServletResponse();
        var chain=mock(FilterChain.class);
        new AccountSessionGuard(db).doFilter(request,response,chain);
        if(allowed) { verify(chain).doFilter(request,response); assertFalse(session.isInvalid()); }
        else { assertEquals(401,response.getStatus()); assertTrue(session.isInvalid()); verifyNoInteractions(chain); }
    }
}
