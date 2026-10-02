package _6.Y2.S1.MTR._6.LaundryLink.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer paymentID;

    @Column(columnDefinition = "decimal(10,2)", nullable = false)
    private Double amount;

    private Integer orderID;

    public Payment(Double amount, Integer orderID) {
        this.amount = amount;
        this.orderID = orderID;
    }
}
