package _6.Y2.S1.MTR._6.LaundryLink.orders;

public interface ManagerOrderSummaryProjection {
    Integer getOrderID();
    Integer getUserID();
    String getFirstName();
    String getMiddleName();
    String getLastName();
    String getEmail();
    String getPhoneNumber();
    Integer getStatusID();
    String getStatusLabel();
    Double getOrderTotal();
}
