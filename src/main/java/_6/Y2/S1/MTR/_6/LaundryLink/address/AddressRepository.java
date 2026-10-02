package _6.Y2.S1.MTR._6.LaundryLink.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findByUserUserIDOrderByIsDefaultDescAddressIDAsc(Integer userID);
    Optional<Address> findByAddressIDAndUserUserID(Integer addressID, Integer userID);
    @Modifying
    @Query("update Address a set a.isDefault = false where a.user.userID = :userID")
    void clearDefaultForUser(@Param("userID") Integer userID);
}
