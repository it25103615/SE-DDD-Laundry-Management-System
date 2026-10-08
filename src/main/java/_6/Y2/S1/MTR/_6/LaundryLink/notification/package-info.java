/**
 * Notification inbox (shared integration code): the cross-module inbox every role sees.
 * Feature services publish through {@code NotificationService}; database triggers cover
 * direct order, payment and delivery writes.
 *
 * <p>Layout follows package-by-feature: the controller and service live here. The signed-in
 * user is resolved through {@code SupportAccess} from the {@code support} package.
 */
package _6.Y2.S1.MTR._6.LaundryLink.notification;
