# Strategy Pattern for Laundry Processing Routes

Module: Laundry Processing Management (`processing` package).

## Summary

The route an order takes through the cleaning stages is now chosen with the **Strategy pattern** (behavioural, Lecture 10). Each route is its own class behind a common `ProcessingRoute` interface, and `ProcessingService` asks the order's route what is allowed instead of branching on a route type.

The same change added two routes and one status:

- **Shoe Cleaning route** and **Ironing route**, next to the existing Wash and Dry-Clean routes.
- **Status 21, "Quality Inspection"**. Every route ends there, and the quality check, packing and "Mark as Ready" happen at that status (they used to happen while the order was still at Ironing).

All paths below are relative to `src/main/java/_6/Y2/S1/MTR/_6/LaundryLink/processing/` unless they start with another folder.

## The problem

Before the change the route was an enum, and every rule checked it with a condition. This is the "traditional approach" the design patter lecture's `ShoppingCart` example starts from.

```java
// ProcessingTransitions.java (before)
public enum Route { WASH, DRY_CLEAN }

public static Integer nextStage(int current, Route route) {
    return switch (current) {
        case VERIFYING_ITEMS -> route == Route.DRY_CLEAN ? DRY_CLEAN : WASHING;
        case WASHING -> route == Route.WASH ? DRYING : null;
        case DRYING -> route == Route.WASH ? IRONING : null;
        case DRY_CLEAN -> route == Route.DRY_CLEAN ? IRONING : null;
        default -> null;
    };
}

public static Set<Integer> reworkTargets(Route route) {
    return route == Route.DRY_CLEAN ? Set.of(DRY_CLEAN, IRONING) : Set.of(WASHING, DRYING, IRONING);
}
```

- A route's stages were spread across several `switch` arms, so no single place showed one route from start to finish.
- A third or fourth route would have meant editing every one of those conditions.
- The board query repeated the route rule a second time in SQL (`CASE WHEN ... THEN 'DRY_CLEAN' ELSE 'WASH'`).

## The routes

Every route starts after Verifying Items (8) and ends at Quality Inspection (21).

| Route | Class | Service on the order | Stages | Rework stages after a failed check |
| --- | --- | --- | --- | --- |
| Wash (default) | `WashRoute` | Wash and Fold, or any service without its own route | 8 → Washing (9) → Drying (10) → Ironing (11) → 21 | Washing, Drying, Ironing |
| Dry-Clean | `DryCleanRoute` | Dry Cleaning | 8 → Dry Clean (19) → Ironing (11) → 21 | Dry Clean, Ironing |
| Shoe Cleaning (new) | `ShoeCleanRoute` | Shoe Cleaning | 8 → Washing (9) → Drying (10) → 21 | Washing, Drying |
| Ironing (new) | `IroningRoute` | Ironing | 8 → Ironing (11) → 21 | Ironing |

Two steps are the same for every route, so they are not part of the strategy:

- **Receiving:** In Shop (7) → Verifying Items (8) happens only by counting the items.
- **Release:** Quality Inspection (21) → Awaiting Delivery (12) happens only through "Mark as Ready", after a passed quality check and packing.

## How the pattern maps to the code

| Lecture role | Lecture example | LaundryLink class |
| --- | --- | --- |
| Strategy interface | `PaymentStrategy` | `ProcessingRoute` |
| Concrete strategies | `CreditCardPayment`, `PayPalPayment` | `WashRoute`, `DryCleanRoute`, `ShoeCleanRoute`, `IroningRoute` |
| Context | `ShoppingCart` | `ProcessingService` |
| Choosing the strategy | the client calling `setPaymentStrategy` | `ProcessingTransitions.routeFor(serviceNames)` |

### Strategy interface

```java
public interface ProcessingRoute {
    String name();                      // code sent to the pages: WASH, DRY_CLEAN, SHOE_CLEAN, IRONING
    String serviceName();               // the service this route handles (null for the default route)
    Integer nextStage(int current);     // the single next stage, or null
    List<Integer> reworkTargets();      // stages a failed check may send the order back to

    default boolean canAdvance(int current, int target) {   // the same rule for every route
        Integer next = nextStage(current);
        return next != null && next == target;
    }
}
```

### A concrete strategy

Each route reads top to bottom in one small class.

```java
public class ShoeCleanRoute implements ProcessingRoute {
    public String name() { return "SHOE_CLEAN"; }
    public String serviceName() { return "Shoe Cleaning"; }

    public Integer nextStage(int current) {
        return switch (current) {
            case VERIFYING_ITEMS -> WASHING;
            case WASHING -> DRYING;
            case DRYING -> QUALITY_INSPECTION;
            default -> null;
        };
    }

    public List<Integer> reworkTargets() { return List.of(WASHING, DRYING); }
}
```

### Choosing the strategy

The strategy is chosen at run time from the order's data. `ProcessingTransitions.routeFor` takes the service names on the order's lines:

- An order has one service. The route whose `serviceName()` matches it is used.
- Anything else follows the Wash route, which has every cleaning stage. That covers a service with no route of its own (for example Express Wash), an order with no lines, and an older order that mixes services.

### The context

`ProcessingService` holds no route conditions. It picks the route once per request and calls it.

```java
ProcessingRoute route = routeOf(repository.findLines(orderID));
if (!route.canAdvance(order.statusID(), targetStatusID)) {
    throw new ApiException(HttpStatus.CONFLICT, invalidTransitionMessage(order, targetStatusID, route));
}
repository.updateStatus(orderID, targetStatusID);
```

The rules that are the same for every route stay in the context: receiving, the quality check, packing and release.

## Quality Inspection (status 21)

A shoe order ends at Drying, so "the quality check happens at Ironing" no longer worked for every route. The check now has a status of its own.

| Action | Before | Now |
| --- | --- | --- |
| Record the quality check | order at Ironing (11) | order at Quality Inspection (21) |
| Failed check | moved back to the rework stage; "redo Ironing" left the order where it was | always moves 21 → the rework stage, so every rework is in the status log |
| Mark as packed | at Ironing, after a passed check | at Quality Inspection, after a passed check |
| Mark as Ready | Ironing (11) → Awaiting Delivery (12) | Quality Inspection (21) → Awaiting Delivery (12) |

An order that was at Ironing with a passed check before the change is not lost: move it on to Quality Inspection and it can be packed and released with the check it already has.

## Files changed

| Area | File | Change |
| --- | --- | --- |
| Strategy | `ProcessingRoute.java` | New interface |
| Strategy | `WashRoute.java`, `DryCleanRoute.java`, `ShoeCleanRoute.java`, `IroningRoute.java` | New concrete strategies |
| Selection | `ProcessingTransitions.java` | `Route` enum and its conditions removed; `routeFor` returns a `ProcessingRoute`; `QUALITY_INSPECTION = 21` added |
| Context | `ProcessingService.java` | Uses the route object; quality check, pack and ready tied to status 21 |
| Board | `ProcessingRepository.java` | Route picked by `routeFor` instead of a second rule in SQL; status 21 included |
| Pages | `src/main/resources/static/js/staff/order-processing.js` | Timelines for the four routes; quality check form shown at status 21 |
| Pages | `src/main/resources/static/js/staff/processing-board.js` | "Quality inspection" column; route label for the new routes |
| Database | `database/migrations/013_quality_inspection_and_routes.sql` | Adds status 21, a Shoe Cleaning test order, and a read-only check of orders in processing |
| Database | `initialize_database.sql`, `database/ddd_assignment2_sample_data.sql`, `scripts/Initialize-SupportDatabase.ps1` | Status 21 in the seed data; migration 013 added to the script |
| Tests | `src/test/.../processing/ProcessingRouteTest.java` | Replaces `ProcessingTransitionsTest`; covers all four routes and the selection rule |
| Tests | `ProcessingServiceTest.java`, `ProcessingIntegrationTest.java` | Updated for the new routes and status 21 |

The API shape did not change: `route` is still a text field on the order, with two new values (`SHOE_CLEAN`, `IRONING`).

## Adding another route

1. Create a class that implements `ProcessingRoute` (for example `ExpressWashRoute`), returning the service name it handles.
2. Add one object of it to `SERVICE_ROUTES` in `ProcessingTransitions`.
3. Add its timeline to `STAGES` and a line to `ROUTE_NOTES` in `order-processing.js`, and a label to `ROUTE_LABELS` in `processing-board.js`.

`ProcessingService` and the existing route classes are not edited.

## Consequences

- **Open/Closed:** a new route is a new class; the existing rules are not touched.
- **Readable:** each route is one short class instead of conditions spread over several methods.
- **One rule, one place:** the board and the order page pick the route through the same method.
- **Testable:** each route is tested on its own, with no database or Spring.
- **Cost:** five small files instead of one enum, and a new route still needs its timeline added to the staff page script.

## Testing

Run the processing tests from the project root in PowerShell:

```powershell
.\mvnw.cmd test "-Dtest=Processing*Test"
```

If Maven reports that `JAVA_HOME` is not defined, point it at your JDK first:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\<your-jdk-folder>"
```

`ProcessingIntegrationTest` uses the real database and needs migration 013 applied; its quality check case skips itself when status 21 is missing.

### Manual check in the app

Sign in as staff (`sam@staff.com`) and open the Processing Board.

| Order | Expected path |
| --- | --- |
| Shoe Cleaning order | Verifying Items → Washing → Drying → Quality Inspection (Ironing is refused) |
| Ironing order | In Shop → receive items → Verifying Items → Ironing → Quality Inspection (Washing is refused) |
| Any order at Quality Inspection | Failed check sends it back to a stage on its route; Passed → pack → Mark as Ready → Awaiting Delivery |