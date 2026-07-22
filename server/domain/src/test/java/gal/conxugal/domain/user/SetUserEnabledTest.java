package gal.conxugal.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class SetUserEnabledTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Mock
  private UserRepository userRepository;

  private SetUserEnabled setUserEnabled;

  @BeforeEach
  void setUp() {
    setUserEnabled = new SetUserEnabled(userRepository);
  }

  @Test
  void enables_an_account_regardless_of_role_or_admin_counts() {
    UUID userId = UUID.randomUUID();

    setUserEnabled.setEnabled(userId, true);

    verify(userRepository).updateEnabled(userId, true);
  }

  @Test
  void disables_non_admin_account() {
    User user = user(Role.USER, true);
    when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

    setUserEnabled.setEnabled(user.id(), false);

    verify(userRepository).updateEnabled(user.id(), false);
  }

  @Test
  void disables_an_admin_when_another_enabled_admin_remains() {
    User target = user(Role.ADMIN, true);
    when(userRepository.findById(target.id())).thenReturn(Optional.of(target));
    when(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(2L);

    setUserEnabled.setEnabled(target.id(), false);

    verify(userRepository).updateEnabled(target.id(), false);
  }

  @Test
  void refuses_to_disable_the_only_remaining_enabled_admin() {
    User onlyAdmin = user(Role.ADMIN, true);
    when(userRepository.findById(onlyAdmin.id())).thenReturn(Optional.of(onlyAdmin));
    when(userRepository.countByRoleAndEnabled(Role.ADMIN, true)).thenReturn(1L);

    assertThatThrownBy(() -> setUserEnabled.setEnabled(onlyAdmin.id(), false))
        .isInstanceOf(LastEnabledAdminException.class);
    verify(userRepository, never()).updateEnabled(onlyAdmin.id(), false);
  }

  @Test
  void disabling_an_already_disabled_admin_does_not_trip_the_guard() {
    User alreadyDisabled = user(Role.ADMIN, false);
    when(userRepository.findById(alreadyDisabled.id())).thenReturn(Optional.of(alreadyDisabled));

    setUserEnabled.setEnabled(alreadyDisabled.id(), false);

    verify(userRepository).updateEnabled(alreadyDisabled.id(), false);
  }

  private static User user(Role role, boolean enabled) {
    return new User(
        UUID.randomUUID(), "user-" + UUID.randomUUID() + "@example.com", "stored-hash", role,
        enabled, CREATED_AT);
  }
}
