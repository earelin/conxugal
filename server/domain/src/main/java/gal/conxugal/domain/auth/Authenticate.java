package gal.conxugal.domain.auth;

import gal.conxugal.domain.time.Clock;
import io.micronaut.data.exceptions.DataAccessException;
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
      passwordEncoder.matchAgainstDummyHash(password);
      return Optional.empty();
    }

    User foundUser = user.get();
    if (!passwordEncoder.matches(password, foundUser.passwordHash())) {
      return Optional.empty();
    }

    return Optional.of(recordLogin(foundUser));
  }

  private User recordLogin(User user) {
    Instant loginInstant = clock.instant();
    try {
      userRepository.updateLastLoginAt(user.id(), loginInstant);
    } catch (DataAccessException e) {
      LOG.error("Failed to record last login for user {}", user.id(), e);
      return user;
    }
    return new User(user.id(), user.email(), user.passwordHash(), user.role(), loginInstant);
  }
}
