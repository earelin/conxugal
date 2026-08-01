package gal.conxugal.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindCurrentUserTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Mock
  private UserRepository userRepository;

  private FindCurrentUser findCurrentUser;

  @BeforeEach
  void setUp() {
    findCurrentUser = new FindCurrentUser(userRepository);
  }

  @Test
  void returns_the_account_found_by_email() {
    User ana =
        new User(new UserId(UUID.randomUUID()), "ana@example.com", "stored-hash", Role.ADMIN, true,
            CREATED_AT);
    when(userRepository.findByEmail("ana@example.com")).thenReturn(Optional.of(ana));

    User result = findCurrentUser.find("ana@example.com");

    assertThat(result).isEqualTo(ana);
  }

  @Test
  void throws_when_no_account_exists_for_the_email() {
    when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> findCurrentUser.find("missing@example.com"))
        .isInstanceOf(NoSuchElementException.class);
  }
}
