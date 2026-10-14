/* ApiException
 * INHERITANCE: exception subclass inherits standard exception behavior.
 */
package _6.Y2.S1.MTR._6.LaundryLink.common;

import org.springframework.http.HttpStatus;

// OOP — Inheritance: this type extends the declared parent and inherits its applicable contract or
// behaviour.
public class ApiException extends RuntimeException {
  public final HttpStatus status;

  public ApiException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }
}
