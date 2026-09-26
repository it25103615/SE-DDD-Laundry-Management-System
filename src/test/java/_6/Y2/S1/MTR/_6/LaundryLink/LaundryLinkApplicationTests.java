package _6.Y2.S1.MTR._6.LaundryLink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import _6.Y2.S1.MTR._6.LaundryLink.security.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.AdministrationService;
import _6.Y2.S1.MTR._6.LaundryLink.service.support.ReportService;
import _6.Y2.S1.MTR._6.LaundryLink.service.shared.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class LaundryLinkApplicationTests {
	@Autowired AdministrationService administration;
	@Autowired ReportService reports;
	@Autowired NotificationService notifications;
	@Autowired JdbcTemplate db;
	@Autowired PasswordEncoder passwordEncoder;
	@Autowired MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void stakeholderDemoCredentialsMatchStoredHashes() {
		var credentials = java.util.Map.of(
			"anna@customer.com", "Anna1234",
			"ravi@rider.com", "Ravi1234",
			"sam@staff.com", "Sam1234",
			"cathy@csm.com", "Cathy1234",
			"maya@manager.com", "Maya1234",
			"oliver@owner.com", "Oliver1234");
		credentials.forEach((email, password) -> {
			var rows = db.queryForList("SELECT password,active FROM users WHERE LOWER(email)=LOWER(?)", email);
			assertEquals(1, rows.size(), "Missing or duplicate demo account: " + email);
			assertEquals(true, rows.getFirst().get("active"), "Inactive demo account: " + email);
			assertTrue(passwordEncoder.matches(password, String.valueOf(rows.getFirst().get("password"))), "Password mismatch: " + email);
		});
	}

	@Test
	void stakeholderDemoAccountsCanLogInAndReachTheirDashboard() throws Exception {
		var credentials = java.util.Map.of(
			"anna@customer.com", new String[]{"Anna1234", "/html/customer/dashboard.html"},
			"ravi@rider.com", new String[]{"Ravi1234", "/html/rider/dashboard.html"},
			"sam@staff.com", new String[]{"Sam1234", "/html/staff/dashboard.html"},
			"cathy@csm.com", new String[]{"Cathy1234", "/html/admin/customer-service-manager/dashboard.html"},
			"maya@manager.com", new String[]{"Maya1234", "/html/admin/manager/dashboard.html"},
			"oliver@owner.com", new String[]{"Oliver1234", "/html/admin/owner/dashboard.html"});
		for (var entry : credentials.entrySet()) {
			var login = mvc.perform(formLogin("/login").userParameter("email").user(entry.getKey()).password(entry.getValue()[0]))
				.andExpect(authenticated().withUsername(entry.getKey()))
				.andExpect(redirectedUrl(entry.getValue()[1]))
				.andReturn();
			mvc.perform(get(entry.getValue()[1]).session((MockHttpSession) login.getRequest().getSession(false)))
				.andExpect(status().isOk());
		}
	}

	@Test
	void ownerCanOpenOwnerReportsAndAdministrationPages() throws Exception {
		var login = mvc.perform(formLogin("/login").userParameter("email").user("oliver@owner.com").password("Oliver1234"))
			.andExpect(authenticated().withUsername("oliver@owner.com"))
			.andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/html/admin/owner/reports.html").session(session)).andExpect(status().isOk());
		mvc.perform(get("/html/admin/owner/administration_overview.html").session(session)).andExpect(status().isOk());
	}

	@Test
	void managerCanOpenLinkedSupportPagesButOwnerPagesReturnToPortal() throws Exception {
		var login = mvc.perform(formLogin("/login").userParameter("email").user("maya@manager.com").password("Maya1234"))
			.andExpect(authenticated().withUsername("maya@manager.com"))
			.andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/html/admin/customer-service-manager/orders.html").session(session)).andExpect(status().isOk());
		mvc.perform(get("/html/admin/customer-service-manager/complaints.html").session(session)).andExpect(status().isOk());
		mvc.perform(get("/html/admin/owner/reports.html").session(session))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
	}

	@Test
	void administrationAndReportsReadTheLiveSchema() {
		var owner = new Actor(0, "Integration owner", "OWNER", false);
		assertTrue(administration.catalog(owner).containsKey("services"));
		assertNotNull(administration.staff(owner, null));
		assertTrue(administration.roles(owner).containsKey("roles"));
		assertTrue(reports.report(owner, null, null, null).containsKey("alerts"));
	}

	@Test
	@Transactional
	void notificationInboxAndDatabaseEventsWork() {
		Integer customerId = db.queryForObject("SELECT TOP 1 userID FROM users WHERE UPPER(type)='CUSTOMER' ORDER BY userID", Integer.class);
		assertNotNull(customerId);
		var customer = new Actor(customerId, "Integration customer", "CUSTOMER", false);
		notifications.notifyUser(customerId, "TEST", "Test notification", "Testing the inbox.", "/html/customer/dashboard.html", "TEST", 1);
		var inbox = notifications.inbox(customer, 30);
		assertTrue(((Number) inbox.get("unread")).intValue() > 0);
		@SuppressWarnings("unchecked") var items = (java.util.List<java.util.Map<String,Object>>) inbox.get("items");
		long notificationId = ((Number) items.getFirst().get("id")).longValue();
		notifications.markRead(customer, notificationId);

		Integer orderId = db.queryForObject("INSERT INTO orders(statusID,userID) VALUES ((SELECT MIN(statusID) FROM status),?); SELECT CAST(SCOPE_IDENTITY() AS INT)", Integer.class, customerId);
		assertNotNull(orderId);
		db.update("INSERT INTO payments(amount,orderID,paymentStatus) VALUES (100.00,?,'VERIFIED')", orderId);
		assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipientID=? AND category='ORDER' AND relatedID=?", Integer.class, customerId, orderId));
		assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM notifications WHERE recipientID=? AND category='PAYMENT' AND link LIKE ?", Integer.class, customerId, "%orderId=" + orderId));
	}

}
