package _6.Y2.S1.MTR._6.LaundryLink.orders;

import _6.Y2.S1.MTR._6.LaundryLink.orderlines.OrderLine;
import _6.Y2.S1.MTR._6.LaundryLink.entity.shared.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statusID")
    private Status status;

    // User Management owns the users entity; retain its foreign-key column without duplicating that module's domain model.
    private Integer userID;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> orderLines = new ArrayList<>();
}
