package gal.conxugal.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListUsersTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Mock
  private UserRepository userRepository;

  private ListUsers listUsers;

  @BeforeEach
  void setUp() {
    listUsers = new ListUsers(userRepository);
  }

  @Test
  void returns_every_account_enabled_and_disabled() {
    User enabled =
        new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.ADMIN, true,
            CREATED_AT);
    User disabled =
        new User(UUID.randomUUID(), "iago@example.com", "stored-hash", Role.USER, false,
            CREATED_AT);
    when(userRepository.findAll()).thenReturn(List.of(enabled, disabled));

    List<User> result = listUsers.list();

    assertThat(result).containsExactly(enabled, disabled);
  }
}
