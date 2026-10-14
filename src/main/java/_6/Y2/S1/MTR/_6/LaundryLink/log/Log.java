/* Log
 * ENTITY: database-mapped domain state. Private fields encapsulate state; Lombok getters/setters or explicit methods control access. JPA relationships model associations, not class inheritance.
 */
package _6.Y2.S1.MTR._6.LaundryLink.log;

import _6.Y2.S1.MTR._6.LaundryLink.status.Status;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.*;

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

  // @ManyToOne
  // @JoinColumn(name = "orderID")
  private Integer orderID;
  // TODO: Once Order class exists update the orderID to order and use the Order class
}
