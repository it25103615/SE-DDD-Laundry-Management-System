package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;

/**
 * Concrete strategy: the ironing-only route. The items arrive clean, so there is no washing or
 * drying.
 * <pre>
 *   Verifying Items (8) -> Ironing (11) -> Quality Inspection (21)
 * </pre>
 */
public class IroningRoute implements ProcessingRoute {

    @Override
    public String name() {
        return "IRONING";
    }

    @Override
    public String serviceName() {
        return "Ironing";
    }

    @Override
    public Integer nextStage(int current) {
        return switch (current) {
            case VERIFYING_ITEMS -> IRONING;
            case IRONING -> QUALITY_INSPECTION;
            default -> null;
        };
    }

    @Override
    public List<Integer> reworkTargets() {
        return List.of(IRONING);
    }
}
