package _6.Y2.S1.MTR._6.LaundryLink.payment;

import java.time.LocalDateTime;

public class PaymentManagementOrderSummary {
    private final Integer orderID;
    private final Integer customerID;
    private final String customerName;
    private final String orderStatus;
    private final LocalDateTime orderDate;

    public PaymentManagementOrderSummary(
            Integer orderID,
            Integer customerID,
            String customerName,
            String orderStatus,
            LocalDateTime orderDate
    ) {
        this.orderID = orderID;
        this.customerID = customerID;
        this.customerName = customerName;
        this.orderStatus = orderStatus;
        this.orderDate = orderDate;
    }

    public Integer getOrderID() {
        return orderID;
    }

    public Integer getCustomerID() {
        return customerID;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }
}
