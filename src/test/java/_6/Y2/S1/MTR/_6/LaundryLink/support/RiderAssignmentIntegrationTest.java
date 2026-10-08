package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RiderAssignmentIntegrationTest {
    @Autowired RiderAssignmentService service;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    int rider() { return db.queryForObject("SELECT userID FROM users WHERE email='ravi@rider.com'",Integer.class); }
    int task(int status) {
        int customer=db.queryForObject("SELECT userID FROM users WHERE email='anna@customer.com'",Integer.class);
        int order=db.queryForObject("SET NOCOUNT ON; INSERT INTO orders(statusID,userID) VALUES(?,?); SELECT CAST(SCOPE_IDENTITY() AS INT)",Integer.class,status,customer);
        return db.queryForObject("SET NOCOUNT ON; INSERT INTO delivery(orderID,userID,pickup_scheduled) VALUES(?,?,DATEADD(DAY,1,SYSDATETIME())); SELECT CAST(SCOPE_IDENTITY() AS INT)",Integer.class,order,customer);
    }
    MockHttpSession login(String email,String password) throws Exception {
        return (MockHttpSession)mvc.perform(formLogin("/login").userParameter("email").user(email).password(password)).andReturn().getRequest().getSession(false);
    }
    @ParameterizedTest @ValueSource(ints={3,12})
    void assigningWaitingTaskUpdatesRiderAndStatusAndRejectsRepeat(int status) {
        int task=task(status); int rider=rider();
        var tasks=(List<?>)service.list().get("tasks");
        assertTrue(tasks.stream().anyMatch(row->((Number)((Map<?,?>)row).get("id")).intValue()==task));
        service.assign(task,rider);
        var saved=db.queryForMap("SELECT o.statusID,d.pickup_riderID,d.delivery_riderID FROM delivery d JOIN orders o ON o.orderID=d.orderID WHERE deliverID=?",task);
        assertEquals(status==3?4:13,((Number)saved.get("statusID")).intValue());
        assertEquals(rider,((Number)saved.get(status==3?"pickup_riderID":"delivery_riderID")).intValue());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.assign(task,rider)).getStatusCode().value());
    }
    @Test void inactiveRiderIsRejectedAndTaskStaysWaiting() {
        int task=task(3);int rider=rider();db.update("UPDATE users SET active=0 WHERE userID=?",rider);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.assign(task,rider)).getStatusCode().value());
        assertNull(db.queryForObject("SELECT pickup_riderID FROM delivery WHERE deliverID=?",Integer.class,task));
    }
    @Test void nonRiderCannotBeAssigned() {
        int task=task(3);int customer=db.queryForObject("SELECT userID FROM users WHERE email='anna@customer.com'",Integer.class);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.assign(task,customer)).getStatusCode().value());
    }
    @Test void managerCanAssignButCustomerCannotAndBodyIsValidated() throws Exception {
        int task=task(3);var customer=login("anna@customer.com","Anna1234");
        mvc.perform(get("/api/support/rider-assignments").session(customer)).andExpect(status().isForbidden());
        var manager=login("maya@manager.com","Maya1234");
        mvc.perform(put("/api/support/rider-assignments/"+task).session(manager).with(csrf()).contentType("application/json").content("{\"riderId\":0}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/support/rider-assignments/"+task).session(manager).with(csrf()).contentType("application/json").content("{\"riderId\":"+rider()+"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.message").exists());
        var riderSession=login("ravi@rider.com","Ravi1234");
        mvc.perform(get("/api/rider/my-work").session(riderSession)).andExpect(status().isOk());
    }
}
