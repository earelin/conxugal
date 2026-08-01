package gal.conxugal.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.time.Clock;
import gal.conxugal.domain.user.PasswordEncoder;
import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserId;
import gal.conxugal.domain.user.UserRepository;
import java.time.Instant;
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
  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  private final Clock clock = () -> FIXED_INSTANT;

  private Authenticate authenticate;

  @BeforeEach
  void setUp() {
    authenticate = new Authenticate(userRepository, passwordEncoder, clock);
  }

  @Test
  void succeeds_and_returns_user_stamped_with_the_login_instant() {
    User user = enabledUser("ana@example.com", Role.ADMIN);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).isPresent();
    assertThat(result.get().lastLoginAt()).isEqualTo(FIXED_INSTANT);
    verify(userRepository).updateLastLoginAt(user.id(), FIXED_INSTANT);
  }

  @Test
  void succeeds_when_recording_the_last_login_fails() {
    User user = enabledUser("ana@example.com", Role.ADMIN);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
    doThrow(new RuntimeException("connection lost"))
        .when(userRepository)
        .updateLastLoginAt(user.id(), FIXED_INSTANT);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).contains(user);
  }

  @Test
  void fails_for_known_email_and_wrong_password_and_records_nothing() {
    User user = enabledUser("ana@example.com", Role.USER);
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

  @Test
  void fails_for_disabled_account_after_the_password_check_succeeds() {
    User user =
        new User(
            new UserId(UUID.randomUUID()), "ana@example.com", "stored-hash", Role.USER, false,
                CREATED_AT);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).isEmpty();
    verify(userRepository, never()).updateLastLoginAt(any(), any());
  }

  @Test
  void succeeds_for_re_enabled_account() {
    User user = enabledUser("ana@example.com", Role.USER);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

    Optional<User> result = authenticate.authenticate("ana@example.com", "correct-password");

    assertThat(result).isPresent();
  }

  private static User enabledUser(String email, Role role) {
    return new User(new UserId(UUID.randomUUID()), email, "stored-hash", role, true, CREATED_AT);
  }
}
