package gal.conxugal.domain.user;

/** Thrown by {@link CreateUser} when an account with the given email already exists. */
public class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException(String email) {
    super("An account with email " + email + " already exists");
  }
}
