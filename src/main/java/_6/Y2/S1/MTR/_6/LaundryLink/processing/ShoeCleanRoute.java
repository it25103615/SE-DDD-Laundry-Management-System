/* ShoeCleanRoute
 * ABSTRACTION / POLYMORPHISM: implements the declared contract. @Override identifies the contract methods supplied by this type.
 */
package _6.Y2.S1.MTR._6.LaundryLink.processing;

import static _6.Y2.S1.MTR._6.LaundryLink.processing.ProcessingTransitions.*;

import java.util.List;

/**
 * Concrete strategy: the shoe cleaning route. Shoes are washed and dried but never ironed.
 *
 * <pre>
 *   Verifying Items (8) -> Washing (9) -> Drying (10) -> Quality Inspection (21)
 * </pre>
 */
public class ShoeCleanRoute implements ProcessingRoute {

  @Override
  public String name() {
    return "SHOE_CLEAN";
  }

  @Override
  public String serviceName() {
    return "Shoe Cleaning";
  }

  @Override
  public Integer nextStage(int current) {
    return switch (current) {
      case VERIFYING_ITEMS -> WASHING;
      case WASHING -> DRYING;
      case DRYING -> QUALITY_INSPECTION;
      default -> null;
    };
  }

  @Override
  public List<Integer> reworkTargets() {
    return List.of(WASHING, DRYING);
  }
}
