/**
 * Laundry processing (Perera G.A.T.L.): everything that happens to an order between the rider
 * dropping it at the shop (status 7, In Shop) and it being ready for delivery (status 12,
 * Awaiting Delivery) - receiving and counting items, the cleaning stages, issue reports and
 * the quality check.
 *
 * <p>The cleaning stages are a Strategy pattern: {@link ProcessingRoute} is the strategy
 * interface, with one class per route (wash, dry-clean, shoe cleaning, ironing), and
 * {@link ProcessingService} is the context. See
 * {@code Documentation/Design Patterns/Strategy-Pattern-Processing-Routes.md}.
 *
 * <p>Layout follows package-by-feature: controller, services, repositories and DTOs for the
 * feature live here. Every status change goes through the database procedure
 * {@code dbo.sp_UpdateProcessingStatus}, which also writes the log row.
 */
package _6.Y2.S1.MTR._6.LaundryLink.processing;