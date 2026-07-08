package gal.conxugal.domain.auth;

import jakarta.inject.Singleton;
import java.util.Optional;

/**
 * Authenticate use case: finds the user by email and verifies the password against
 * the stored hash, returning the user on success or an indistinct failure otherwise.
 *
 * <p>For an unknown email there is no stored hash to compare against, so the check is
 * not short-circuited: {@link PasswordEncoder#matchAgainstDummyHash} runs instead, at
 * the adapter's normalized cost, so an unknown email and a wrong password are not
 * separable by execution time.
 */
@Singleton
public class Authenticate {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public Authenticate(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public Optional<User> authenticate(String email, String password) {
    Optional<User> user = userRepository.findByEmail(email);

    if (user.isEmpty()) {
      passwordEncoder.matchAgainstDummyHash(password);
      return Optional.empty();
    }

    boolean passwordMatches = passwordEncoder.matches(password, user.get().passwordHash());
    return passwordMatches ? user : Optional.empty();
  }
}
