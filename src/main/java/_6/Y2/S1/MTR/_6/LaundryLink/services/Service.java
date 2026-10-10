package _6.Y2.S1.MTR._6.LaundryLink.services;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "services")
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer serviceID;

    private String serviceName;

    // Switched off from the admin service catalogue when a service is no longer offered. Sent
    // with /api/services so the customer's Choose a service page can leave such services out.
    private Boolean active;
}
