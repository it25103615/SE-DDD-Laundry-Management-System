package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;

/**
 * The processing status IDs and the choice of an order's route, kept free of any database or
 * Spring code so they are easy to read and to unit test.
 *
 * <p>The rules about which stage may follow which are a Strategy pattern: each route is a
 * {@link ProcessingRoute} implementation, picked here from the service on the order's lines:
 * <pre>
 *   Wash route:           8 -> Washing (9) -> Drying (10) -> Ironing (11) -> Quality Inspection (21)
 *   Dry-Clean route:      8 -> Dry Clean (19) -> Ironing (11) -> Quality Inspection (21)
 *   Shoe Cleaning route:  8 -> Washing (9) -> Drying (10) -> Quality Inspection (21)
 *   Ironing route:        8 -> Ironing (11) -> Quality Inspection (21)
 * </pre>
 * Moving In Shop (7) -> Verifying Items (8) happens only by receiving the items, and
 * Quality Inspection (21) -> Awaiting Delivery (12) happens only by marking a quality-checked,
 * packed order as ready, so neither is a plain "next stage" step on any route.
 */
public final class ProcessingTransitions {
    // Status IDs from the status table
    public static final int IN_SHOP = 7;
    public static final int VERIFYING_ITEMS = 8;
    public static final int WASHING = 9;
    public static final int DRYING = 10;
    public static final int IRONING = 11;
    public static final int AWAITING_DELIVERY = 12;
    public static final int DRY_CLEAN = 19;
    /** Where the quality check, packing and "Mark as Ready" happen, on every route. */
    public static final int QUALITY_INSPECTION = 21;

    /** Every status an order can have while the processing module owns it (board and dashboard). */
    public static final List<Integer> PROCESSING_STATUSES =
            List.of(IN_SHOP, VERIFYING_ITEMS, WASHING, DRY_CLEAN, DRYING, IRONING, QUALITY_INSPECTION);

    /** Used when no other route matches (Wash and Fold, Express Wash, ...). */
    private static final ProcessingRoute DEFAULT_ROUTE = new WashRoute();

    /** The routes tied to one service. The routes hold no data, so one object of each is shared. */
    private static final List<ProcessingRoute> SERVICE_ROUTES =
            List.of(new DryCleanRoute(), new ShoeCleanRoute(), new IroningRoute());

    private ProcessingTransitions() {
    }

    /**
     * Picks the route (the strategy) from the service names on an order's lines. An order has one
     * service, and the route that names that service is used. Anything else - a service with no
     * route of its own, an order with no lines, or an older order that mixes services - follows
     * the wash route, which has every cleaning stage.
     */
    public static ProcessingRoute routeFor(List<String> serviceNames) {
        List<String> services = serviceNames.stream().distinct().toList();
        if (services.size() != 1) {
            return DEFAULT_ROUTE;
        }
        return SERVICE_ROUTES.stream()
                .filter(route -> route.serviceName().equalsIgnoreCase(services.getFirst()))
                .findFirst()
                .orElse(DEFAULT_ROUTE);
    }
}
