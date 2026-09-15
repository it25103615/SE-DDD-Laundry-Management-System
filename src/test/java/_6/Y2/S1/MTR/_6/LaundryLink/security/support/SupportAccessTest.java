package _6.Y2.S1.MTR._6.LaundryLink.security.support;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportAccessTest {
    @Test void demoHeaderCannotAuthenticateInDefaultProfile() {
        var db=mock(JdbcTemplate.class);
        var access=new SupportAccess(db);
        var error=assertThrows(ResponseStatusException.class,()->access.actor(null,9));
        assertEquals(401,error.getStatusCode().value());verifyNoInteractions(db);
    }
    @Test void customerCannotReadBusinessReportsOrSettings() {
        var access=new SupportAccess(null);
        assertThrows(ResponseStatusException.class,()->access.manager(new SupportAccess.Actor(1,"Customer","CUSTOMER",true)));
    }
    @Test void csmCanHandleCasesButCannotManageSettings() {
        var access=new SupportAccess(null);
        var actor=new SupportAccess.Actor(3,"CSM","CSM",false);
        assertDoesNotThrow(()->access.staff(actor));
        assertThrows(ResponseStatusException.class,()->access.manager(actor));
    }
    @Test void operationalStaffCanParticipateButCannotCoordinateCases() {
        var access=new SupportAccess(null);
        var staff=new SupportAccess.Actor(4,"Laundry Staff","STAFF",false);
        var rider=new SupportAccess.Actor(5,"Delivery Staff","RIDER",false);
        assertDoesNotThrow(()->access.staff(staff));
        assertDoesNotThrow(()->access.staff(rider));
        assertThrows(ResponseStatusException.class,()->access.coordinator(staff));
        assertThrows(ResponseStatusException.class,()->access.coordinator(rider));
    }
}
