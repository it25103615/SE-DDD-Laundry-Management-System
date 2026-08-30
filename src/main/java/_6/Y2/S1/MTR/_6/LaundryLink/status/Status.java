package _6.Y2.S1.MTR._6.LaundryLink.status;

import jakarta.persistence.*;

@Entity
@Table(name = "status")
public class Status {
    @Id
    private Integer statusID;
    private String statusLabel;

    public Integer getStatusID() {
        return statusID;
    }

    public String getStatusLabel() {
        return statusLabel;
    }
}
