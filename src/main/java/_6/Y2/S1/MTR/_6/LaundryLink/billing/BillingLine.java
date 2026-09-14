package _6.Y2.S1.MTR._6.LaundryLink.billing;

import java.math.BigDecimal;

public class BillingLine {
    private final Integer orderLineID;
    private final Integer itemID;
    private final Integer serviceID;
    private final Integer quantity;
    private final BigDecimal linePrice;
    private final BigDecimal lineTotal;

    public BillingLine(Integer orderLineID, Integer itemID, Integer serviceID, Integer quantity, BigDecimal linePrice) {
        this.orderLineID = orderLineID;
        this.itemID = itemID;
        this.serviceID = serviceID;
        this.quantity = quantity;
        this.linePrice = linePrice;
        this.lineTotal = linePrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Integer getOrderLineID() {
        return orderLineID;
    }

    public Integer getItemID() {
        return itemID;
    }

    public Integer getServiceID() {
        return serviceID;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getLinePrice() {
        return linePrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
