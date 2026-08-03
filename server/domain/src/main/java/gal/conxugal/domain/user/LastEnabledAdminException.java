package gal.conxugal.domain.user;

/**
 * Thrown by {@link SetUserEnabled} when disabling an account would drop the enabled
 * {@code ADMIN} count to zero, so the administration area can never become
 * unreachable.
 */
public class LastEnabledAdminException extends RuntimeException {

  private final UserId userId;

  public LastEnabledAdminException(UserId userId) {
    super("Cannot disable the only remaining enabled ADMIN account: %s".formatted(userId));
    this.userId = userId;
  }

  public UserId getUserId() {
    return userId;
  }
}
