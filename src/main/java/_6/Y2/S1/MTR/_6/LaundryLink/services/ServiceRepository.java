/* ServiceRepository
 * REPOSITORY: persistence boundary. JPA repository interfaces are implemented by Spring; JDBC implementations execute explicit SQL.
 * INHERITANCE: interface extends JpaRepository and inherits repository operations; Spring supplies its implementation.
 */
package _6.Y2.S1.MTR._6.LaundryLink.services;

import org.springframework.data.jpa.repository.JpaRepository;

// OOP — Abstraction: this interface describes operations that implementing classes must provide.
// OOP — Inheritance: this repository interface inherits CRUD operations from JpaRepository; Spring
// creates the implementation.
public interface ServiceRepository extends JpaRepository<Service, Integer> {}
