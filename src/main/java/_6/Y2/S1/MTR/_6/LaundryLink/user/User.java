package _6.Y2.S1.MTR._6.LaundryLink.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userID;
    @Column(name = "firstName", length = 50) private String firstName;
    @Column(name = "middleName", length = 50) private String middleName;
    @Column(name = "lastName", length = 50) private String lastName;
    @Column(name = "email", length = 100, unique = true) private String email;
    @JsonIgnore @Column(name = "password", length = 255) private String password;
    @Column(name = "phoneNumber",columnDefinition = "CHAR(10)") private String phoneNumber;
    @Enumerated(EnumType.STRING) @Column(name = "type", length = 20) private UserRole type;
}
