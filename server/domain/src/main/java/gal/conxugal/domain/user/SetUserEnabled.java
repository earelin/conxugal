package gal.conxugal.domain.user;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * Enables or disables an account. Disabling refuses to drop the enabled {@code ADMIN}
 * count to zero. Only when the target is a currently-enabled admin does this take a
 * {@code FOR UPDATE} lock on the whole enabled-admin set before deciding: a concurrent
 * call disabling a different admin then blocks until this transaction commits, and
 * re-evaluates against the now-committed state — closing the race where two concurrent
 * disables of the last two enabled admins could otherwise both succeed. Skipping the
 * lock for every other case (non-admin target, already-disabled target) is safe because
 * roles never change after creation, so those cases can never newly become the guarded
 * one mid-transaction.
 */
@Singleton
public class SetUserEnabled {

  private final UserRepository userRepository;

  public SetUserEnabled(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public User setEnabled(UUID userId, boolean enabled) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (!enabled && user.role() == Role.ADMIN && user.enabled()
        && userRepository.findByRoleAndEnabledForUpdate(Role.ADMIN, true).size() <= 1) {
      throw new LastEnabledAdminException(userId);
    }
    userRepository.updateEnabled(userId, enabled);
    return new User(
        user.id(), user.email(), user.passwordHash(), user.role(), enabled, user.createdAt(),
        user.lastLoginAt());
  }
}
