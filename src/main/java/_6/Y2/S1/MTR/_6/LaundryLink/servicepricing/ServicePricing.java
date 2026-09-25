package _6.Y2.S1.MTR._6.LaundryLink.servicepricing;

import _6.Y2.S1.MTR._6.LaundryLink.items.Item;
import _6.Y2.S1.MTR._6.LaundryLink.services.Service;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@IdClass(ServicePricingId.class)
@Table(name = "servicePricing")
public class ServicePricing {
    @Id
    @Column(name = "itemID")
    private Integer itemID;

    @Id
    @Column(name = "serviceID")
    private Integer serviceID;

    @Column(columnDefinition = "decimal(10,2)", nullable = false)
    private Double price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemID", insertable = false, updatable = false)
    @JsonIgnore
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serviceID", insertable = false, updatable = false)
    @JsonIgnore
    private Service service;
}
