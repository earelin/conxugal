package gal.conxugal.domain.user;

import java.util.UUID;

/** Thrown when an operation targets an account id that doesn't exist. */
public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(UUID userId) {
    super("No account exists with id: " + userId);
  }
}
