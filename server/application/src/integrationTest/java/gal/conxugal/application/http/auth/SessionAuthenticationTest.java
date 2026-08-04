package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class SessionAuthenticationTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(TestUserFactory.normalUser());
    seedUser(TestUserFactory.adminUser());
  }

  @Test
  void valid_login_establishes_session_cookie() {
    HttpResponse<?> response = client.exchange(loginRequest("user@example.com", "user-password"));

    assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNotBlank();
  }

  /**
   * The browser-side half of the defence the CSRF filter no longer provides over
   * {@code /api/**}: a cross-site caller gets no session to spend. Lax rather than Strict, so
   * a link followed into the application still arrives authenticated.
   */
  @Test
  void session_cookie_is_withheld_from_cross_site_requests() {
    HttpResponse<?> response = client.exchange(loginRequest("user@example.com", "user-password"));

    List<String> sessionCookies = response.getHeaders()
        .getAll(HttpHeaders.SET_COOKIE)
        .stream()
        .filter(setCookie -> setCookie.startsWith("SESSION="))
        .toList();

    assertThat(sessionCookies)
        .singleElement()
        .asString()
        .contains("SameSite=Lax");
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

  /**
   * A cookie value the session layer cannot read identifies nobody, which is the 401 the
   * contract declares — not the 400 an unguarded decode of it would report.
   */
  @Test
  void request_carrying_an_unreadable_session_cookie_is_rejected_as_unauthenticated() {
    HttpRequest<?> request = HttpRequest.GET("/api/data")
        .header(HttpHeaders.COOKIE, "SESSION=not-a-readable-session");

    assertThat(statusOf(request)).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(loginRequest(email, password));
    return sessionCookieOf(response);
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
