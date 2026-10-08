package _6.Y2.S1.MTR._6.LaundryLink.orderlines;

import _6.Y2.S1.MTR._6.LaundryLink.orders.Order;
import _6.Y2.S1.MTR._6.LaundryLink.servicepricing.ServicePricing;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orderLines")
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderLineID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orderID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "itemID", referencedColumnName = "itemID", nullable = false),
            @JoinColumn(name = "serviceID", referencedColumnName = "serviceID", nullable = false)
    })
    private ServicePricing servicePricing;

    private Integer quantity;
    @Column(columnDefinition = "decimal(10,2)", nullable = false)
    private Double linePrice;
}
