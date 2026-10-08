package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import java.io.Serializable;
import java.util.Objects;

public class ServicePricingId implements Serializable {
    private Integer itemID;
    private Integer serviceID;

    public ServicePricingId() {
    }

    public ServicePricingId(Integer itemID, Integer serviceID) {
        this.itemID = itemID;
        this.serviceID = serviceID;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ServicePricingId that)) {
            return false;
        }
        return Objects.equals(itemID, that.itemID)
                && Objects.equals(serviceID, that.serviceID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemID, serviceID);
    }
}
