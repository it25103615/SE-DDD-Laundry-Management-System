package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;

/**
 * Strategy interface (Strategy pattern): one way of taking an order through the cleaning stages.
 *
 * <p>Each kind of service has its own implementation ({@link WashRoute}, {@link DryCleanRoute},
 * {@link ShoeCleanRoute}, {@link IroningRoute}), so {@link ProcessingService} never needs an
 * if/else on the route: it asks the order's route what comes next. Adding a new route means
 * adding a new class and listing it in {@link ProcessingTransitions#routeFor}.
 *
 * <p>Every route starts after Verifying Items (8) and ends by moving to Quality Inspection (21).
 * Receiving the items (7 -> 8) and releasing the order (21 -> 12) are the same for every route,
 * so they are not part of the strategy.
 */
public interface ProcessingRoute {

    /** The code sent to the staff pages, e.g. "WASH". The pages use it to pick the timeline. */
    String name();

    /**
     * The service (by its name in the services table) whose orders follow this route, or
     * {@code null} for the default route used when no other route matches.
     */
    String serviceName();

    /**
     * The single stage an order may move to from {@code current} with a plain status change, or
     * {@code null} when there is none (e.g. In Shop, Quality Inspection, or a stage that is not
     * on this route).
     */
    Integer nextStage(int current);

    /**
     * Stages a failed quality check may send the order back to, in workflow order. Only stages
     * on this route are listed.
     */
    List<Integer> reworkTargets();

    /** True only when {@code target} is exactly the next stage. The same rule for every route. */
    default boolean canAdvance(int current, int target) {
        Integer next = nextStage(current);
        return next != null && next == target;
    }
}
