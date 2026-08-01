package gal.conxugal.domain.user;

/** Thrown when an operation targets an account id that doesn't exist. */
public class UserNotFoundException extends RuntimeException {

  private final UserId userId;

  public UserNotFoundException(UserId userId) {
    super("No account exists with id: " + userId);
    this.userId = userId;
  }

  public UserId getUserId() {
    return userId;
  }
}
