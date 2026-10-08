/**
 * User and account management (Vipusha V.): customer registration, the signed-in user's
 * profile and password, the customer dashboard summary, and staff account administration.
 *
 * <p>Layout follows package-by-feature: controllers and services live here, with request and
 * response types in {@code account.dto}.
 *
 * <p>Two services exist side by side. {@code AccountService} / {@code AccountServiceImpl}
 * (JPA) back {@code AccountController}; {@code AccountProfileService} (JDBC) backs
 * {@code AuthController} and {@code ProfileController}. Likewise {@code RegistrationRequest}
 * belongs to the former and {@code CustomerRegistrationRequest} to the latter.
 */
package _6.Y2.S1.MTR._6.LaundryLink.account;
