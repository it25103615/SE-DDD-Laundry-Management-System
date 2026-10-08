package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the processing routes (the Strategy pattern's concrete strategies) and for the
 * rule that picks an order's route. No database, no Spring.
 */
class ProcessingRouteTest {

    /** The route object for a route code, so the cases below can name routes in a CSV row. */
    static ProcessingRoute route(String name) {
        return switch (name) {
            case "WASH" -> new WashRoute();
            case "DRY_CLEAN" -> new DryCleanRoute();
            case "SHOE_CLEAN" -> new ShoeCleanRoute();
            case "IRONING" -> new IroningRoute();
            default -> throw new IllegalArgumentException(name);
        };
    }

    /** Every allowed and refused single step on the four routes. */
    @ParameterizedTest(name = "{0}: {1} -> {2} allowed={3}")
    @CsvSource({
            // wash route: 8 -> 9 -> 10 -> 11 -> 21
            "WASH, 8, 9, true",
            "WASH, 9, 10, true",     // TC-LP07 Washing -> Drying
            "WASH, 10, 11, true",
            "WASH, 11, 21, true",
            "WASH, 9, 12, false",    // TC-LP08 skipping to Awaiting Delivery
            "WASH, 9, 11, false",    // skipping Drying
            "WASH, 10, 21, false",   // skipping Ironing
            "WASH, 8, 19, false",    // wash orders never go to Dry Clean
            // dry clean route: 8 -> 19 -> 11 -> 21 (no drying)
            "DRY_CLEAN, 8, 19, true",
            "DRY_CLEAN, 19, 11, true",
            "DRY_CLEAN, 11, 21, true",
            "DRY_CLEAN, 19, 10, false",
            "DRY_CLEAN, 8, 9, false",
            // shoe cleaning route: 8 -> 9 -> 10 -> 21 (no ironing)
            "SHOE_CLEAN, 8, 9, true",
            "SHOE_CLEAN, 9, 10, true",
            "SHOE_CLEAN, 10, 21, true",
            "SHOE_CLEAN, 10, 11, false",
            "SHOE_CLEAN, 8, 19, false",
            // ironing route: 8 -> 11 -> 21 (no washing or drying)
            "IRONING, 8, 11, true",
            "IRONING, 11, 21, true",
            "IRONING, 8, 9, false",
            "IRONING, 8, 21, false",
            // In Shop and Quality Inspection are left only by receiving / quality check + ready
            "WASH, 7, 8, false",
            "WASH, 21, 12, false",
            "SHOE_CLEAN, 21, 12, false"
    })
    void onlyTheNextStageOnTheRouteIsAllowed(String route, int from, int to, boolean allowed) {
        assertEquals(allowed, route(route).canAdvance(from, to));
    }

    @Test
    void everyRouteEndsAtQualityInspection() {
        assertEquals(QUALITY_INSPECTION, new WashRoute().nextStage(IRONING));
        assertEquals(QUALITY_INSPECTION, new DryCleanRoute().nextStage(IRONING));
        assertEquals(QUALITY_INSPECTION, new ShoeCleanRoute().nextStage(DRYING));
        assertEquals(QUALITY_INSPECTION, new IroningRoute().nextStage(IRONING));
    }

    @Test
    void aStageThatIsNotOnTheRouteHasNoNextStage() {
        assertNull(new ShoeCleanRoute().nextStage(IRONING));
        assertNull(new IroningRoute().nextStage(WASHING));
        assertNull(new DryCleanRoute().nextStage(DRYING));
        assertNull(new WashRoute().nextStage(DRY_CLEAN));
    }

    @Test
    void reworkTargetsStayOnTheRouteInWorkflowOrder() {
        assertEquals(List.of(WASHING, DRYING, IRONING), new WashRoute().reworkTargets());
        assertEquals(List.of(DRY_CLEAN, IRONING), new DryCleanRoute().reworkTargets());
        assertEquals(List.of(WASHING, DRYING), new ShoeCleanRoute().reworkTargets());
        assertEquals(List.of(IRONING), new IroningRoute().reworkTargets());
    }

    @Test
    void routeIsPickedFromTheOrdersService() {
        assertInstanceOf(WashRoute.class, routeFor(List.of("Wash and Fold")));
        assertInstanceOf(DryCleanRoute.class, routeFor(List.of("Dry Cleaning")));
        assertInstanceOf(ShoeCleanRoute.class, routeFor(List.of("Shoe Cleaning")));
        assertInstanceOf(IroningRoute.class, routeFor(List.of("Ironing")));
        // Several lines of the same service are still one service.
        assertInstanceOf(IroningRoute.class, routeFor(List.of("Ironing", "Ironing")));
    }

    @Test
    void anythingElseFollowsTheWashRoute() {
        assertInstanceOf(WashRoute.class, routeFor(List.of("Express Wash")));               // no route of its own
        assertInstanceOf(WashRoute.class, routeFor(List.of()));                             // no lines
        assertInstanceOf(WashRoute.class, routeFor(List.of("Shoe Cleaning", "Ironing")));   // older mixed order
    }
}
