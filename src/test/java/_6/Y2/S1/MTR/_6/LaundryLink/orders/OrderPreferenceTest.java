package _6.Y2.S1.MTR._6.LaundryLink.orders;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests for the helpers that turn preference codes into the stored string and back into labels. */
class OrderPreferenceTest {

    @Test
    void findsAPreferenceByItsCode() {
        assertEquals(Optional.of(OrderPreference.FRAGRANCE_FREE), OrderPreference.fromCode("fragrance-free"));
        assertEquals(Optional.of(OrderPreference.HANG_DRY), OrderPreference.fromCode("hang-dry"));
        // Spaces around a code are ignored.
        assertEquals(Optional.of(OrderPreference.HANGERS), OrderPreference.fromCode(" hangers "));
    }

    @Test
    void doesNotFindAnUnknownOrMissingCode() {
        assertEquals(Optional.empty(), OrderPreference.fromCode("extra-starch"));
        // Codes are matched exactly: the enum name and other spellings are not codes.
        assertEquals(Optional.empty(), OrderPreference.fromCode("FRAGRANCE_FREE"));
        assertEquals(Optional.empty(), OrderPreference.fromCode(""));
        assertEquals(Optional.empty(), OrderPreference.fromCode(null));
    }

    @Test
    void joinsCodesInEnumOrderWithoutDuplicates() {
        String stored = OrderPreference.toStoredValue(List.of(
                OrderPreference.HANGERS, OrderPreference.FRAGRANCE_FREE, OrderPreference.HANGERS));

        assertEquals("fragrance-free,hangers", stored);
    }

    @Test
    void storesNothingWhenThereAreNoPreferences() {
        assertNull(OrderPreference.toStoredValue(List.of()));
        assertNull(OrderPreference.toStoredValue(null));
    }

    @Test
    void everyPreferenceFitsInTheColumnTogether() {
        // orders.preferences is VARCHAR(100); all preferences ticked at once must still fit.
        String stored = OrderPreference.toStoredValue(List.of(OrderPreference.values()));

        assertEquals("fragrance-free,hypoallergenic,hang-dry,hangers", stored);
    }

    @Test
    void turnsAStoredValueBackIntoLabels() {
        assertEquals(
                List.of("Fragrance-free detergent", "Return shirts on hangers"),
                OrderPreference.labelsOf("fragrance-free,hangers"));
    }

    @Test
    void hasNoLabelsForAnOrderWithoutPreferences() {
        assertEquals(List.of(), OrderPreference.labelsOf(null));
        assertEquals(List.of(), OrderPreference.labelsOf("  "));
    }

    @Test
    void showsAnUnknownStoredCodeAsItIs() {
        assertEquals(
                List.of("Hang-dry delicate items", "extra-starch"),
                OrderPreference.labelsOf("hang-dry, extra-starch"));
    }
}
