package _6.Y2.S1.MTR._6.LaundryLink.address;

import _6.Y2.S1.MTR._6.LaundryLink.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer addressID;
    @Column(length = 50) private String nickname;
    @Column(length = 100) private String street;
    @Column(length = 30) private String city;
    @Column(name = "state", length = 30) private String state;
    @Column(name = "DeliveryInstructions", length = 250) private String deliveryInstructions;
    @Column(nullable = false) private Boolean isDefault;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userID")
    private User user;
}
