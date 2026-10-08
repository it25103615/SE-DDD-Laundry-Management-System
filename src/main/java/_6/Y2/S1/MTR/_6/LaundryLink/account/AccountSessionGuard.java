package _6.Y2.S1.MTR._6.LaundryLink.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance coordination for the application's synchronous, email-based account lookups. */
public final class AccountSessionGuard extends OncePerRequestFilter {
    private static final ReentrantLock[] LOCKS = new ReentrantLock[256];
    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }
    private final JdbcTemplate db;

    public AccountSessionGuard(JdbcTemplate db) { this.db = db; }

    public static boolean isRetiredEmail(String email) {
        return email != null && email.toLowerCase(Locale.ROOT)
                .matches("deleted_[0-9]+_[0-9a-f]{32}@deleted\\.invalid");
    }

    /** Email remains the username for existing modules; the ID never changes with email reuse. */
    public static final class AccountPrincipal extends User {
        private static final long serialVersionUID = 1L;
        private final int userId;

        public AccountPrincipal(int userId, String email, String password, boolean active, String role) {
            super(email, password == null ? "" : password,
                    active && !isRetiredEmail(email) && role != null, true, true, true,
                    role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            this.userId = userId;
        }

        public int userId() { return userId; }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }
        if (!(authentication.getPrincipal() instanceof AccountPrincipal account)) {
            reject(request, response);
            return;
        }
        ReentrantLock lock = LOCKS[Math.floorMod(account.userId(), LOCKS.length)];
        lock.lock();
        try {
            // Never recover identity by looking up the current owner of the login email.
            var emails = db.query("SELECT email FROM users WHERE userID=? AND active=1",
                    (rs, row) -> rs.getString("email"), account.userId());
            if (emails.size() != 1 || isRetiredEmail(emails.getFirst())
                    || !account.getUsername().equalsIgnoreCase(emails.getFirst())) {
                reject(request, response);
                return;
            }
            // Includes controller work and transaction commit: deletion cannot race a prior request.
            chain.doFilter(request, response);
        } finally {
            lock.unlock();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        if (request.getServletPath().startsWith("/api/")
                || request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Sign in again. This account session is no longer valid.\"}");
        } else {
            response.sendRedirect(request.getContextPath() + "/html/auth/login.html");
        }
    }
}
