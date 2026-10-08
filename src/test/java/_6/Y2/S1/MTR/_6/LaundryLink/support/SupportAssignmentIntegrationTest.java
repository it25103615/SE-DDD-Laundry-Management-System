package _6.Y2.S1.MTR._6.LaundryLink.support;

import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.account.AccountSessionGuard.AccountPrincipal;
import static _6.Y2.S1.MTR._6.LaundryLink.support.dto.SupportRequests.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupportAssignmentIntegrationTest {
    @Autowired SupportService service;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    Actor actor(String email) {
        return db.queryForObject("SELECT userID,CONCAT(firstName,' ',lastName),UPPER(type) FROM users WHERE email=?",
                (r,n)->new Actor(r.getInt(1),r.getString(2),r.getString(3),false),email);
    }
    CaseUpdate update(Map<String,Object> item,String status,int assignee,String note) {
        return new CaseUpdate(status,"Normal",assignee,note,((Number)item.get("version")).intValue());
    }
    @ParameterizedTest
    @ValueSource(strings={"ADMIN","OWNER","MANAGER","CSM","CUSTOMER_SERVICE_MANAGER","STAFF","RIDER"})
    void everyStaffRoleReceivesOrderLinkedCaseCanChatAndResolve(String role) throws Exception {
        // All case, notification, message and role changes roll back after each test.
        db.update("UPDATE users SET type=? WHERE email='ravi@rider.com'",role);
        Actor assignee=actor("ravi@rider.com"), customer=actor("anna@customer.com");
        Actor coordinator=db.queryForObject("SELECT TOP 1 userID,CONCAT(firstName,' ',lastName),UPPER(type) FROM users WHERE active=1 AND UPPER(type)='CSM' AND userID<>?",(r,n)->new Actor(r.getInt(1),r.getString(2),r.getString(3),false),assignee.id());
        int order=db.queryForObject("SET NOCOUNT ON; INSERT INTO orders(statusID,userID) VALUES(1,?); SELECT CAST(SCOPE_IDENTITY() AS INT)",Integer.class,customer.id());
        var item=service.create(customer,new CaseInput("Question","Assignment verification","Please check my order",order,null,null,"Pickup & delivery"));
        int id=((Number)item.get("id")).intValue();
        assertEquals("Pickup & delivery",item.get("topic"));
        assertNull(item.get("assigneeId"));
        assertTrue(service.cases(coordinator,null,null,"Question",null,null,0,"Pickup & delivery").stream().anyMatch(p->((Number)p.get("id")).intValue()==id));
        assertTrue(service.cases(coordinator,null,null,null,null,null,0,"Payments & billing").stream().noneMatch(p->((Number)p.get("id")).intValue()==id));
        assertTrue(((List<?>)service.options(coordinator).get("staff")).stream().anyMatch(p->((Number)((Map<?,?>)p).get("id")).intValue()==assignee.id()));
        item=service.handle(coordinator,id,update(item,"Assigned",assignee.id(),"Please investigate"));
        assertEquals("/html/support/cases.html?caseId="+id,db.queryForObject("SELECT TOP 1 link FROM notifications WHERE recipientID=? AND relatedID=? AND title='Support case assigned' ORDER BY notificationID DESC",String.class,assignee.id(),id));
        assertTrue(service.cases(assignee,null,null,null,null,null,0).stream().anyMatch(p->((Number)p.get("id")).intValue()==id));
        var principal=new AccountPrincipal(assignee.id(),"ravi@rider.com","unused",true,role);
        mvc.perform(get("/html/support/dashboard.html").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/html/support/cases.html").with(user(principal))).andExpect(status().isOk());
        assertEquals(order,((Number)service.detail(assignee,id).get("orderId")).intValue());
        assertEquals(Set.of("CSM","CUSTOMER_SERVICE_MANAGER").contains(role),service.detail(assignee,id).containsKey("history"));
        assertFalse(service.detail(customer,id).containsKey("history"));
        assertTrue(service.detail(coordinator,id).containsKey("history"));
        if (!assignee.coordinator()) {
            var captured=item;
            assertEquals(403,assertThrows(ResponseStatusException.class,()->service.handle(assignee,id,update(captured,"In Review",coordinator.id(),"Transfer"))).getStatusCode().value());
            Actor other=actor("maya@manager.com");
            assertEquals(404,assertThrows(ResponseStatusException.class,()->service.detail(other,id)).getStatusCode().value());
            assertEquals(404,assertThrows(ResponseStatusException.class,()->service.message(other,id,new MessageInput("Unauthorised"))).getStatusCode().value());
        }
        mvc.perform(post("/api/support/cases/"+id+"/messages").with(user(principal)).with(csrf())
                .contentType("application/json").content("{\"message\":\"Tomorrow at 8 p.m.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].message").value("Tomorrow at 8 p.m."));
        var customerPrincipal=new AccountPrincipal(customer.id(),"anna@customer.com","unused",true,"CUSTOMER");
        mvc.perform(get("/api/support/cases/"+id).with(user(customerPrincipal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].message").value("Tomorrow at 8 p.m."));
        assertEquals("Tomorrow at 8 p.m.",((Map<?,?>)((List<?>)service.detail(coordinator,id).get("messages")).getFirst()).get("message"));
        item=service.message(customer,id,new MessageInput("Thank you."));
        assertEquals(2,((List<?>)item.get("messages")).size());
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipientID=? AND relatedID=? AND title='Customer replied'",Integer.class,assignee.id(),id)>0);
        item=service.handle(assignee,id,update(item,"In Review",assignee.id(),"Investigating"));
        item=service.handle(assignee,id,update(item,"Resolved",assignee.id(),"Order issue corrected and confirmed."));
        assertEquals("Resolved",service.detail(customer,id).get("status"));
        assertEquals("Resolved",item.get("status"));
    }
}
