package gal.conxugal.domain.auth;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.UUID;

/**
 * Enables or disables an account. Disabling refuses to drop the enabled {@code ADMIN}
 * count to zero. This check reads {@link UserRepository#findAll} before writing, so
 * guarding it against a concurrent request racing the same account is an
 * infrastructure-level concern, not this use case's.
 */
@Singleton
public class SetUserEnabled {

  private final UserRepository userRepository;

  public SetUserEnabled(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public void setEnabled(UUID userId, boolean enabled) {
    if (!enabled && isLastEnabledAdmin(userId)) {
      throw new LastEnabledAdminException(userId);
    }
    userRepository.updateEnabled(userId, enabled);
  }

  private boolean isLastEnabledAdmin(UUID userId) {
    List<User> users = userRepository.findAll();
    return users.stream()
        .filter(user -> userId.equals(user.id()))
        .findFirst()
        .filter(user -> user.role() == Role.ADMIN && user.enabled())
        .map(user -> countEnabledAdmins(users) <= 1)
        .orElse(false);
  }

  private long countEnabledAdmins(List<User> users) {
    return users.stream().filter(user -> user.role() == Role.ADMIN && user.enabled()).count();
  }
}
