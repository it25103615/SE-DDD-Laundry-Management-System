/* ManagerOrderSummaryProjection
 * INTERFACE: abstraction contract; implementations supply behavior. Where multiple implementations are called through this type, runtime polymorphism applies.
 *
 */
package _6.Y2.S1.MTR._6.LaundryLink.orders;

// OOP — Abstraction: this interface describes operations that implementing classes must provide.
public interface ManagerOrderSummaryProjection {
  Integer getOrderID();

  Integer getUserID();

  String getFirstName();

  String getMiddleName();

  String getLastName();

  String getEmail();

  String getPhoneNumber();

  Integer getStatusID();

  String getStatusLabel();

  Double getOrderTotal();
}
