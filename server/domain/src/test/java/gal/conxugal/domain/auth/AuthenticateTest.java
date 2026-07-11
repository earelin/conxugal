package gal.conxugal.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.data.exceptions.DataAccessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-11T10:15:30Z");

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

  private Authenticate authenticate;

  @BeforeEach
  void setUp() {
    authenticate = new Authenticate(userRepository, passwordEncoder, clock);
  }

  @Test
  void succeeds_and_returns_user_stamped_with_the_login_instant() {
    User user = new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).isPresent();
    assertThat(result.get().lastLoginAt()).isEqualTo(FIXED_INSTANT);
    verify(userRepository).updateLastLoginAt(user.id(), FIXED_INSTANT);
  }

  @Test
  void succeeds_when_recording_the_last_login_fails() {
    User user = new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
    doThrow(new DataAccessException("connection lost"))
        .when(userRepository)
        .updateLastLoginAt(user.id(), FIXED_INSTANT);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).contains(user);
  }

  @Test
  void fails_for_known_email_and_wrong_password_and_records_nothing() {
    User user = new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

    Optional<User> result = authenticate.authenticate("ana@example.com", "wrong-password");

    assertThat(result).isEmpty();
    verify(userRepository, never()).updateLastLoginAt(any(), any());
  }

  @Test
  void fails_for_unknown_email_and_records_nothing() {
    when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

    Optional<User> result = authenticate.authenticate("ghost@example.com", "whatever");

    assertThat(result).isEmpty();
    verify(userRepository, never()).updateLastLoginAt(any(), any());
  }
}
