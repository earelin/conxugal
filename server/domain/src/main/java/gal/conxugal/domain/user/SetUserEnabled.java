package gal.conxugal.domain.user;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Enables or disables an account. Disabling refuses to drop the enabled {@code ADMIN}
 * count to zero. This check reads the account and the admin count before writing, so
 * guarding it against a concurrent request racing the same account is an
 * infrastructure-level concern, not this use case's.
 */
@Singleton
public class SetUserEnabled {

  private final UserRepository userRepository;

  public SetUserEnabled(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public void setEnabled(UUID userId, boolean enabled) {
    if (!enabled && isLastEnabledAdmin(userId)) {
      throw new LastEnabledAdminException(userId);
    }
    userRepository.updateEnabled(userId, enabled);
  }

  private boolean isLastEnabledAdmin(UUID userId) {
    return userRepository.findById(userId)
        .filter(user -> user.role() == Role.ADMIN && user.enabled())
        .map(user -> userRepository.countByRoleAndEnabled(Role.ADMIN, true) <= 1)
        .orElse(false);
  }
}
