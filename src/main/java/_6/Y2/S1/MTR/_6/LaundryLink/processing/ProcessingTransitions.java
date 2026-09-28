package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;
import java.util.Set;

/**
 * The processing workflow rules, kept free of any database or Spring code so they are easy to
 * read and to unit test.
 *
 * <p>An order follows one of two routes, decided by the services on its order lines:
 * <pre>
 *   Wash route:       Verifying Items (8) -> Washing (9) -> Drying (10) -> Ironing (11)
 *   Dry-Clean route:  Verifying Items (8) -> Dry-Clean (19) -> Ironing (11)  (no drying)
 * </pre>
 * Moving In Shop (7) -> Verifying Items (8) happens only by receiving the items,
 * and Ironing (11) -> Awaiting Delivery (12) happens only by marking a quality-checked, packed order as ready,
 * so neither is a plain "next stage" step here.
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

    /** Every status an order can have while the processing module owns it (board and dashboard). */
    public static final List<Integer> PROCESSING_STATUSES =
            List.of(IN_SHOP, VERIFYING_ITEMS, WASHING, DRY_CLEAN, DRYING, IRONING);

    /** The two ways through the cleaning stages. */
    public enum Route { WASH, DRY_CLEAN }

    private ProcessingTransitions() {
    }

    /**
     * Picks the route from the services on an order's lines: any Dry Cleaning line sends the
     * whole order down the dry-clean route.
     */
    public static Route routeFor(List<Integer> serviceIds, Integer dryCleaningServiceId) {
        boolean hasDryCleaning = dryCleaningServiceId != null && serviceIds.contains(dryCleaningServiceId);
        return hasDryCleaning ? Route.DRY_CLEAN : Route.WASH;
    }

    /**
     * The single stage an order may move to from {@code current} with a plain status change, or
     * {@code null} when there is none (e.g. In Shop, Ironing, or a stage that is not on the
     * order's route).
     */
    public static Integer nextStage(int current, Route route) {
        return switch (current) {
            case VERIFYING_ITEMS -> route == Route.DRY_CLEAN ? DRY_CLEAN : WASHING;
            case WASHING -> route == Route.WASH ? DRYING : null;
            case DRYING -> route == Route.WASH ? IRONING : null;
            case DRY_CLEAN -> route == Route.DRY_CLEAN ? IRONING : null;
            default -> null;
        };
    }

    /** True only when {@code target} is exactly the next stage. */
    public static boolean canAdvance(int current, int target, Route route) {
        Integer next = nextStage(current, route);
        return next != null && next == target;
    }

    /**
     * Stages a failed quality check may send the order back. Only stages on the
     * order's own route are allowed; Ironing (11) means "redo the ironing" without moving.
     */
    public static Set<Integer> reworkTargets(Route route) {
        return route == Route.DRY_CLEAN ? Set.of(DRY_CLEAN, IRONING) : Set.of(WASHING, DRYING, IRONING);
    }
}
