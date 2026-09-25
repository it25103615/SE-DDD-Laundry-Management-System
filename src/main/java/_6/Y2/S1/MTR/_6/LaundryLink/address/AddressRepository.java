package _6.Y2.S1.MTR._6.LaundryLink.address;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findByUserUserIDOrderByIsDefaultDescAddressIDAsc(Integer userID);
    Optional<Address> findByAddressIDAndUserUserID(Integer addressID, Integer userID);
    @Modifying
    @Query("update Address a set a.isDefault = false where a.user.userID = :userID")
    void clearDefaultForUser(@Param("userID") Integer userID);
}
