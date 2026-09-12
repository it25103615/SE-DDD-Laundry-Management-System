package _6.Y2.S1.MTR._6.LaundryLink.logs;

import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer logID;

    @ManyToOne
    @JoinColumn(name = "status_before")
    private Status statusBefore;

    @ManyToOne
    @JoinColumn(name = "status_after")
    private Status statusAfter;

    private LocalDate logDate;
    private LocalTime logTime;

    //@ManyToOne
    //@JoinColumn(name = "orderID")
    private Integer orderID;
    //TODO: Once Order class exists update the orderID to order and use the Order class
}
