package _6.Y2.S1.MTR._6.LaundryLink.processing;

import _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Unit tests for the processing workflow rules (no database, no Spring). */
class ProcessingTransitionsTest {

    /** Every allowed and refused single step on both routes. */
    @ParameterizedTest(name = "{0}: {1} -> {2} allowed={3}")
    @CsvSource({
            // wash route: 8 -> 9 -> 10 -> 11
            "WASH, 8, 9, true",
            "WASH, 9, 10, true",     // TC-LP07 Washing -> Drying
            "WASH, 10, 11, true",
            "WASH, 9, 12, false",    // TC-LP08 skipping to Awaiting Delivery
            "WASH, 9, 11, false",    // skipping Drying
            "WASH, 8, 19, false",    // wash orders never go to Dry Clean
            // dry clean route: 8 -> 19 -> 11 (no drying)
            "DRY_CLEAN, 8, 19, true",
            "DRY_CLEAN, 19, 11, true",
            "DRY_CLEAN, 19, 10, false",
            "DRY_CLEAN, 8, 9, false",
            // In Shop and Ironing are left only by receiving / quality check + ready
            "WASH, 7, 8, false",
            "WASH, 11, 12, false"
    })
    void onlyTheNextStageOnTheRouteIsAllowed(Route route, int from, int to, boolean allowed) {
        assertEquals(allowed, canAdvance(from, to, route));
    }

    @Test
    void washingToDryingAllowed() {
        assertEquals(DRYING, nextStage(WASHING, Route.WASH));
    }

    @Test
    void dryCleanGoesStraightToIroning() {
        assertEquals(IRONING, nextStage(DRY_CLEAN, Route.DRY_CLEAN));
        assertFalse(canAdvance(DRY_CLEAN, DRYING, Route.DRY_CLEAN));
    }

    @Test
    void routeIsReadFromTheServices() {
        int dryCleaning = 3;
        assertEquals(Route.DRY_CLEAN, routeFor(List.of(3), dryCleaning));
        assertEquals(Route.DRY_CLEAN, routeFor(List.of(2, 3), dryCleaning));   // any dry clean line
        assertEquals(Route.WASH, routeFor(List.of(1, 2), dryCleaning));
        assertEquals(Route.WASH, routeFor(List.of(3), null));                  // no Dry Cleaning service at all
    }

    @Test
    void reworkTargetsStayOnTheRoute() {
        assertEquals(Set.of(WASHING, DRYING, IRONING), reworkTargets(Route.WASH));
        assertEquals(Set.of(DRY_CLEAN, IRONING), reworkTargets(Route.DRY_CLEAN));
    }
}
