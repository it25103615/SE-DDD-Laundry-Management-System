package _6.Y2.S1.MTR._6.LaundryLink;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import _6.Y2.S1.MTR._6.LaundryLink.support.SupportAccess.Actor;
import _6.Y2.S1.MTR._6.LaundryLink.support.AdministrationService;
import _6.Y2.S1.MTR._6.LaundryLink.support.ReportService;
import _6.Y2.S1.MTR._6.LaundryLink.notification.NotificationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

	// The Log out button in global-pre.js relies on this contract: POST /logout with a CSRF
	// token ends the session and redirects to the login page. Without the token it is refused,
	// and SecurityConfig's access-denied handler turns that refusal into a redirect to the
	// portal (not a 403), which is why the button checks where the response ended up.
	@Test
	void logoutEndsTheSessionAndRequiresACsrfToken() throws Exception {
		var login = mvc.perform(formLogin("/login").userParameter("email").user("anna@customer.com").password("Anna1234"))
			.andExpect(authenticated().withUsername("anna@customer.com"))
			.andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);

		mvc.perform(post("/logout").session(session))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		assertFalse(session.isInvalid(), "A rejected logout must leave the session signed in");

		mvc.perform(post("/logout").session(session).with(csrf()))
			.andExpect(redirectedUrl("/html/auth/login.html?logout=true"))
			.andExpect(unauthenticated());
		assertTrue(session.isInvalid(), "Logout should invalidate the session");
	}

	// The /api/orders rules in SecurityConfig, tried through the real filter chain.
	// A refused request is redirected to the portal by the access-denied handler.
	@Test
	void customersReachOnlyTheirOwnOrdersWhileStaffCanSeeEveryCustomers() throws Exception {
		int anna = db.queryForObject("SELECT userID FROM users WHERE email='anna@customer.com'", Integer.class);
		int otherCustomer = db.queryForObject("SELECT TOP 1 userID FROM users WHERE UPPER(type)='CUSTOMER' AND userID<>? ORDER BY userID", Integer.class, anna);

		// Not signed in: sent to the login page.
		mvc.perform(get("/api/orders/customer/" + anna)).andExpect(status().is3xxRedirection());

		var customer = (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user("anna@customer.com").password("Anna1234"))
			.andReturn().getRequest().getSession(false);
		mvc.perform(get("/api/orders/customer/" + anna).session(customer)).andExpect(status().isOk());
		mvc.perform(get("/api/orders/customer/" + otherCustomer).session(customer))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		mvc.perform(get("/api/orders/management").session(customer))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		// Placing an order for someone else is refused by OrderController.
		mvc.perform(post("/api/orders").session(customer).with(csrf())
				.contentType("application/json")
				.content("{\"userID\":" + otherCustomer + ",\"orderLines\":[{\"itemID\":1,\"serviceID\":1,\"quantity\":1}]}"))
			.andExpect(status().isForbidden());

		for (String[] account : new String[][]{{"sam@staff.com", "Sam1234"}, {"ravi@rider.com", "Ravi1234"}, {"maya@manager.com", "Maya1234"}, {"oliver@owner.com", "Oliver1234"}}) {
			var session = (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user(account[0]).password(account[1]))
				.andReturn().getRequest().getSession(false);
			mvc.perform(get("/api/orders/customer/" + otherCustomer).session(session)).andExpect(status().isOk());
			mvc.perform(get("/api/orders/management").session(session)).andExpect(status().isOk());
		}
	}

	@Test
	void financeApisRequireLoginAndIgnoreSpoofedUserHeader() throws Exception {
		int anna = db.queryForObject("SELECT userID FROM users WHERE email='anna@customer.com'", Integer.class);

		mvc.perform(get("/api/payments/history").header("X-User-ID", anna))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/api/billing/orders/1/invoice").header("X-User-ID", anna))
			.andExpect(status().is3xxRedirection());
		mvc.perform(get("/api/promotions").header("X-User-ID", anna))
			.andExpect(status().is3xxRedirection());
		mvc.perform(post("/api/payments/orders/1").header("X-User-ID", anna).with(csrf())
				.contentType("application/json")
				.content("{\"paymentMethod\":\"CARD\",\"amount\":100.00}"))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	void customerCannotUseFinanceManagementEndpoints() throws Exception {
		var customer = (MockHttpSession) mvc.perform(formLogin("/login").userParameter("email").user("anna@customer.com").password("Anna1234"))
			.andReturn().getRequest().getSession(false);

		mvc.perform(get("/api/payments/management").session(customer))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		mvc.perform(post("/api/payments/management/1/approve").session(customer).with(csrf()))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		mvc.perform(post("/api/promotions").session(customer).with(csrf())
				.contentType("application/json")
				.content("{\"promotionCode\":\"NOPE\",\"promotionName\":\"Nope\",\"discountType\":\"FIXED_AMOUNT\",\"discountValue\":1,\"minimumOrderAmount\":0,\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-12-31\",\"active\":true}"))
			.andExpect(redirectedUrl("/html/portal.html?accessDenied=true"));
		mvc.perform(get("/api/promotions").session(customer))
			.andExpect(status().isForbidden());
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
