package _6.Y2.S1.MTR._6.LaundryLink.orders;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The preferences a customer can tick on the "Instructions" step of the new-order form.
 *
 * <p>This enum is the single list of allowed preferences. Each one has a short {@code code}
 * (sent by the form and stored in {@code orders.preferences}) and a {@code label} (the text
 * shown to customers and staff). An order's ticked preferences are stored as one
 * comma-separated string of codes, for example {@code "fragrance-free,hangers"}.
 *
 * <p>The codes must match the checkbox values in {@code new_order_instructions.html}.
 */
public enum OrderPreference {
    FRAGRANCE_FREE("fragrance-free", "Fragrance-free detergent"),
    HYPOALLERGENIC("hypoallergenic", "Hypoallergenic detergent"),
    HANG_DRY("hang-dry", "Hang-dry delicate items"),
    HANGERS("hangers", "Return shirts on hangers");

    /** Separates the codes inside the stored string. No code contains a comma. */
    private static final String SEPARATOR = ",";

    private final String code;
    private final String label;

    OrderPreference(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** Finds the preference with this code. Empty when the code is null or not a known one. */
    public static Optional<OrderPreference> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(preference -> preference.code.equals(trimmed))
                .findFirst();
    }

    /**
     * Joins preferences into the string stored in {@code orders.preferences}. Duplicates are
     * dropped and the codes always come out in the order the enum lists them, so the same set
     * of ticks is always stored the same way. Returns null when there are no preferences, so
     * the column stays NULL instead of holding an empty string.
     */
    public static String toStoredValue(Collection<OrderPreference> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }
        // EnumSet removes duplicates and iterates in enum order.
        return EnumSet.copyOf(preferences).stream()
                .map(OrderPreference::getCode)
                .collect(Collectors.joining(SEPARATOR));
    }

    /**
     * Turns a stored string back into display labels, e.g. {@code "fragrance-free,hangers"}
     * into "Fragrance-free detergent" and "Return shirts on hangers". Returns an empty list
     * for NULL or blank. A code this enum does not know (for example one typed directly into
     * the database) is shown as it is stored, so nothing the order holds is hidden.
     */
    public static List<String> labelsOf(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return List.of();
        }
        return Arrays.stream(storedValue.split(SEPARATOR))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> fromCode(code).map(OrderPreference::getLabel).orElse(code))
                .toList();
    }
}
