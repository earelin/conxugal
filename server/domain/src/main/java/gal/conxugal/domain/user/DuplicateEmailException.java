package gal.conxugal.domain.user;

/** Thrown by {@link CreateUser} when an account with the given email already exists. */
public class DuplicateEmailException extends RuntimeException {

  private final String email;

  public DuplicateEmailException(String email) {
    super("An account with email " + email + " already exists");
    this.email = email;
  }

  public String getEmail() {
    return email;
  }
}
