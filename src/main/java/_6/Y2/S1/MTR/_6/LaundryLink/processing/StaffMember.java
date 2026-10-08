package _6.Y2.S1.MTR._6.LaundryLink.processing;

/**
 * The signed-in staff member doing the processing work. Their ID is stored on received items,
 * quality checks and issue reports; the role is re-checked in ProcessingService.
 */
public record StaffMember(int userID, String name, String role) {
}
