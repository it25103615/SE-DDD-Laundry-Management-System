package _6.Y2.S1.MTR._6.LaundryLink.processing;

import java.util.List;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;

/**
 * Concrete strategy: the dry-clean route. There is no drying stage.
 * <pre>
 *   Verifying Items (8) -> Dry Clean (19) -> Ironing (11) -> Quality Inspection (21)
 * </pre>
 */
public class DryCleanRoute implements ProcessingRoute {

    @Override
    public String name() {
        return "DRY_CLEAN";
    }

    @Override
    public String serviceName() {
        return "Dry Cleaning";
    }

    @Override
    public Integer nextStage(int current) {
        return switch (current) {
            case VERIFYING_ITEMS -> DRY_CLEAN;
            case DRY_CLEAN -> IRONING;
            case IRONING -> QUALITY_INSPECTION;
            default -> null;
        };
    }

    @Override
    public List<Integer> reworkTargets() {
        return List.of(DRY_CLEAN, IRONING);
    }
}
