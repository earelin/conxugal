package gal.conxugal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUserTest {

  private static final Instant CREATED_AT = Instant.parse("2026-07-18T09:30:00Z");

  @Mock
  private UserRepository userRepository;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private PasswordGenerator passwordGenerator;

  private CreateUser createUser;

  @BeforeEach
  void setUp() {
    createUser =
        new CreateUser(userRepository, passwordEncoder, passwordGenerator, () -> CREATED_AT);
  }

  @Test
  void creates_an_enabled_account_with_generated_password() {
    when(userRepository.findByEmail("nova@example.com")).thenReturn(Optional.empty());
    GeneratedPassword generatedPassword = new GeneratedPassword("Tg7#kLp2Qw9$mZxR");
    when(passwordGenerator.generate()).thenReturn(generatedPassword);
    when(passwordEncoder.encode("Tg7#kLp2Qw9$mZxR")).thenReturn("hashed-password");
    UUID createdId = UUID.randomUUID();
    when(userRepository.create(any(User.class))).thenAnswer(invocation -> {
      User submitted = invocation.getArgument(0);
      return new User(
          createdId, submitted.email(), submitted.passwordHash(), submitted.role(),
          submitted.enabled(), submitted.createdAt());
    });

    CreatedAccount result = createUser.create("nova@example.com", Role.USER);

    assertThat(result.initialPassword()).isEqualTo(generatedPassword);
    assertThat(result.user().id()).isEqualTo(createdId);
    assertThat(result.user().email()).isEqualTo("nova@example.com");
    assertThat(result.user().passwordHash()).isEqualTo("hashed-password");
    assertThat(result.user().role()).isEqualTo(Role.USER);
    assertThat(result.user().enabled()).isTrue();
    assertThat(result.user().createdAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void refuses_to_create_an_account_with_an_already_used_email() {
    User existing = new User(
        UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN, true, CREATED_AT);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> createUser.create("ana@example.com", Role.ADMIN))
        .isInstanceOf(DuplicateEmailException.class);
    verify(userRepository, never()).create(any(User.class));
  }
}
