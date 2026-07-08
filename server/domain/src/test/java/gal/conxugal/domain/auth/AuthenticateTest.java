package gal.conxugal.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private Authenticate authenticate;

  @BeforeEach
  void setUp() {
    authenticate = new Authenticate(userRepository, passwordEncoder);
  }

  @Test
  void succeeds_and_returns_user_for_known_email_and_matching_password() {
    User user = new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).contains(user);
  }

  @Test
  void fails_for_known_email_and_wrong_password() {
    User user = new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

    Optional<User> result = authenticate.authenticate("ana@example.com", "wrong-password");

    assertThat(result).isEmpty();
  }

  @Test
  void fails_for_unknown_email() {
    when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    Optional<User> result = authenticate.authenticate("ghost@example.com", "whatever");

    assertThat(result).isEmpty();
  }
}
