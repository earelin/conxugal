package gal.conxugal.domain.user;

import java.util.UUID;

/**
 * Thrown by {@link SetUserEnabled} when disabling an account would drop the enabled
 * {@code ADMIN} count to zero, so the administration area can never become
 * unreachable.
 */
public class LastEnabledAdminException extends RuntimeException {

  public LastEnabledAdminException(UUID userId) {
    super("Cannot disable the only remaining enabled ADMIN account: " + userId);
  }
}
