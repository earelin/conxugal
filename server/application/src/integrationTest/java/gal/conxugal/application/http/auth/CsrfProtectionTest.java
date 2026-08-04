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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class CsrfProtectionTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(TestUserFactory.normalUser());
  }

  /**
   * The login form is the surface the token exists for. Refused as unauthorized rather than
   * forbidden because the caller submitting it carries no authentication yet.
   */
  @Test
  void form_submission_without_csrf_token_is_rejected() {
    HttpRequest<?> request =
        HttpRequest.POST("/login", "username=user@example.com&password=user-password")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);

    assertThat(statusOf(request)).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  /** Holding a session is not the same as proving the request was meant. */
  @Test
  void form_submission_carrying_session_but_no_csrf_token_is_rejected() {
    String sessionCookie = loginWithJson();

    HttpRequest<?> request = HttpRequest.POST("/logout", "")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        .header(HttpHeaders.COOKIE, sessionCookie);

    assertThat(statusOf(request)).isEqualTo(HttpStatus.FORBIDDEN.getCode());
  }

  /**
   * The API takes JSON bodies no cross-site form can produce and answers no CORS, so the token
   * would guard nothing there — while the filter, left covering it, rejects every request that
   * carries no content type at all, which is what a body-less DELETE is.
   */
  @Test
  void api_request_without_csrf_token_is_served() {
    String sessionCookie = loginWithJson();

    HttpRequest<?> request = HttpRequest.POST("/api/data", "{}")
        .contentType(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.COOKIE, sessionCookie);

    assertThat(statusOf(request)).isEqualTo(HttpStatus.OK.getCode());
  }

  @Test
  void api_delete_carrying_no_content_type_is_served() {
    String sessionCookie = loginWithJson();

    HttpRequest<?> request = HttpRequest.DELETE("/api/data")
        .header(HttpHeaders.COOKIE, sessionCookie);

    assertThat(statusOf(request)).isEqualTo(HttpStatus.NO_CONTENT.getCode());
  }

  private String loginWithJson() {
    HttpResponse<?> loginResponse = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials("user@example.com", "user-password"))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(loginResponse);
  }

  private int statusOf(HttpRequest<?> request) {
    try {
      return client.exchange(request).getStatus().getCode();
    } catch (HttpClientResponseException e) {
      return e.getStatus().getCode();
    }
  }
}
