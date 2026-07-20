package gal.conxugal.domain.auth;

import gal.conxugal.domain.time.Clock;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticate use case: finds the user by email and verifies the password against
 * the stored hash, returning the user on success or an indistinct failure otherwise.
 * A successful authentication also stamps and persists the user's {@code lastLoginAt},
 * best-effort: a failure to record it does not fail the login.
 *
 * <p>For an unknown email there is no stored hash to compare against, so the check is
 * not short-circuited: {@link PasswordEncoder#matchAgainstDummyHash} runs instead, at
 * the adapter's normalized cost, so an unknown email and a wrong password are not
 * separable by execution time.
 *
 * <p>A disabled account is rejected only after the password check succeeds, so that
 * outcome is likewise indistinct from a wrong password.
 */
@Singleton
public class Authenticate {

  private static final Logger LOG = LoggerFactory.getLogger(Authenticate.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public Authenticate(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  public Optional<User> authenticate(String email, String password) {
    Optional<User> user = userRepository.findByEmail(email);

    if (user.isEmpty()) {
      LOG.warn("No user found with email {}", email);
      passwordEncoder.matchAgainstDummyHash(password);
      return Optional.empty();
    }

    User foundUser = user.get();
    if (!passwordEncoder.matches(password, foundUser.passwordHash())) {
      LOG.warn("Passwords do not match");
      return Optional.empty();
    }

    if (!foundUser.enabled()) {
      LOG.warn("Account for {} is disabled", email);
      return Optional.empty();
    }

    return Optional.of(recordLogin(foundUser));
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private User recordLogin(User user) {
    Instant loginInstant = clock.instant();
    try {
      userRepository.updateLastLoginAt(user.id(), loginInstant);
    } catch (RuntimeException e) {
      // Caught broadly, not as the adapter's own exception type, so the domain stays
      // free of persistence concerns while still honouring the best-effort guarantee
      // against any failure the port's implementation may throw.
      LOG.error("Failed to record last login for user {}", user.id(), e);
      return user;
    }
    return new User(
        user.id(), user.email(), user.passwordHash(), user.role(), user.enabled(),
        user.createdAt(), loginInstant);
  }
}
