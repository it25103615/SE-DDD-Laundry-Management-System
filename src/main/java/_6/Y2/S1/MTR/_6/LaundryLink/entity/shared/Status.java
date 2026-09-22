package _6.Y2.S1.MTR._6.LaundryLink.entity.shared;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "status")
public class Status {
    @Id
    private Integer statusID;
    private String statusLabel;

}
