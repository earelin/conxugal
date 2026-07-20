package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.auth.Authenticate;
import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.User;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationProviderTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-15T09:30:00Z");

  @Mock
  private Authenticate authenticate;

  private UserAuthenticationProvider provider;

  @BeforeEach
  void setUp() {
    provider = new UserAuthenticationProvider(authenticate);
  }

  @Test
  void returns_success_with_user_role_for_valid_credentials() {
    User user =
        new User(UUID.randomUUID(), "ana@example.com", "stored-hash", Role.USER, true,
            CREATED_AT);
    when(authenticate.authenticate("ana@example.com", "correct-password"))
        .thenReturn(Optional.of(user));

    AuthenticationResponse response = provider.authenticate(
        HttpRequest.GET("/login"),
        new UsernamePasswordCredentials("ana@example.com", "correct-password"));

    assertThat(response.isAuthenticated()).isTrue();
    assertThat(response.getAuthentication()).isPresent();
    assertThat(response.getAuthentication().get().getRoles()).containsExactly("USER");
  }

  @Test
  void returns_success_with_admin_role_for_valid_credentials() {
    User user =
        new User(UUID.randomUUID(), "root@example.com", "stored-hash", Role.ADMIN, true,
            CREATED_AT);
    when(authenticate.authenticate("root@example.com", "correct-password"))
        .thenReturn(Optional.of(user));

    AuthenticationResponse response = provider.authenticate(
        HttpRequest.GET("/login"),
        new UsernamePasswordCredentials("root@example.com", "correct-password"));

    assertThat(response.isAuthenticated()).isTrue();
    assertThat(response.getAuthentication()).isPresent();
    assertThat(response.getAuthentication().get().getRoles()).containsExactly("ADMIN");
  }

  @Test
  void returns_credentials_do_not_match_failure_when_authenticate_use_case_rejects() {
    when(authenticate.authenticate("ghost@example.com", "whatever")).thenReturn(Optional.empty());

    AuthenticationResponse response = provider.authenticate(
        HttpRequest.GET("/login"),
        new UsernamePasswordCredentials("ghost@example.com", "whatever"));

    assertThat(response.isAuthenticated()).isFalse();
    assertThat(response.getAuthentication()).isEmpty();
  }
}
