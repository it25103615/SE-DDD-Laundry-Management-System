package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;

/**
 * Concrete strategy: the wash route, also the default for any service without a route of its own.
 * <pre>
 *   Verifying Items (8) -> Washing (9) -> Drying (10) -> Ironing (11) -> Quality Inspection (21)
 * </pre>
 */
public class WashRoute implements ProcessingRoute {

    @Override
    public String name() {
        return "WASH";
    }

    /** The default route: it is not tied to one service. */
    @Override
    public String serviceName() {
        return null;
    }

    @Override
    public Integer nextStage(int current) {
        return switch (current) {
            case VERIFYING_ITEMS -> WASHING;
            case WASHING -> DRYING;
            case DRYING -> IRONING;
            case IRONING -> QUALITY_INSPECTION;
            default -> null;
        };
    }

    @Override
    public List<Integer> reworkTargets() {
        return List.of(WASHING, DRYING, IRONING);
    }
}
