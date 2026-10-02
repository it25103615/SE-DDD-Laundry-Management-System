package _6.Y2.S1.MTR._6.LaundryLink.address;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findByUserUserIDOrderByIsDefaultDescAddressIDAsc(Integer userID);
    Optional<Address> findByAddressIDAndUserUserID(Integer addressID, Integer userID);
    // How many addresses the customer currently has. Addresses they deleted are detached
    // (userID NULL), so they are not counted.
    long countByUserUserID(Integer userID);
    @Modifying
    @Query("update Address a set a.isDefault = false where a.user.userID = :userID")
    void clearDefaultForUser(@Param("userID") Integer userID);
    // Clears the default flag on all of the customer's addresses except one, which is the
    // address about to become (or stay) the default.
    @Modifying
    @Query("update Address a set a.isDefault = false where a.user.userID = :userID and a.addressID <> :keepAddressID")
    void clearDefaultForUserExcept(@Param("userID") Integer userID, @Param("keepAddressID") Integer keepAddressID);
}
