package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class SessionAuthenticationTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void set_up() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
    seedUser(new User(UUID.randomUUID(), "admin@example.com", "admin-password", Role.ADMIN));
  }

  @Test
  void valid_login_establishes_a_session_cookie() {
    HttpResponse<?> response = client.exchange(loginRequest("user@example.com", "user-password"));

    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotBlank();
  }

  @Test
  void authenticated_user_reaches_data_but_is_forbidden_from_admin() {
    String sessionCookie = login("user@example.com", "user-password");

    assertThat(statusOf(protectedRequest("/api/data", sessionCookie)))
        .isEqualTo(HttpStatus.OK.getCode());
    assertThat(statusOf(protectedRequest("/api/admin/reports", sessionCookie)))
        .isEqualTo(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void authenticated_admin_reaches_both_data_and_admin() {
    String sessionCookie = login("admin@example.com", "admin-password");

    assertThat(statusOf(protectedRequest("/api/data", sessionCookie)))
        .isEqualTo(HttpStatus.OK.getCode());
    assertThat(statusOf(protectedRequest("/api/admin/reports", sessionCookie)))
        .isEqualTo(HttpStatus.OK.getCode());
  }

  @Test
  void unauthenticated_request_to_protected_route_is_rejected() {
    assertThat(statusOf(HttpRequest.GET("/api/data"))).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(loginRequest(email, password));
    String setCookieHeader = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    return setCookieHeader.split(";", 2)[0];
  }

  private HttpRequest<?> loginRequest(String email, String password) {
    return HttpRequest.POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE);
  }

  private HttpRequest<?> protectedRequest(String path, String sessionCookie) {
    return HttpRequest.GET(path).header(HttpHeaders.COOKIE, sessionCookie);
  }

  private int statusOf(HttpRequest<?> request) {
    try {
      return client.exchange(request).getStatus().getCode();
    } catch (HttpClientResponseException e) {
      return e.getStatus().getCode();
    }
  }
}
