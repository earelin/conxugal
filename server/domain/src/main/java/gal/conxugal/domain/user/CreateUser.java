package gal.conxugal.domain.user;

import gal.conxugal.domain.time.Clock;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

/**
 * Creates a new account: enforces email uniqueness, generates and hashes the initial
 * password, and persists the account enabled with the current instant as its
 * {@code createdAt}.
 */
@Singleton
public class CreateUser {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordGenerator passwordGenerator;
  private final Clock clock;

  public CreateUser(
      UserRepository userRepository, PasswordEncoder passwordEncoder,
      PasswordGenerator passwordGenerator, Clock clock) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordGenerator = passwordGenerator;
    this.clock = clock;
  }

  @Transactional
  public CreatedAccount create(String email, Role role) {
    if (userRepository.findByEmail(email).isPresent()) {
      throw new DuplicateEmailException(email);
    }
    GeneratedPassword initialPassword = passwordGenerator.generate();
    String passwordHash = passwordEncoder.encode(initialPassword.value());
    User created =
        userRepository.create(new User(null, email, passwordHash, role, true, clock.instant()));
    return new CreatedAccount(created, initialPassword);
  }
}
