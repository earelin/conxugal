package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class LogoutTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    HttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
    configuration.setFollowRedirects(false);
    client = HttpClient.create(embeddedServer.getURL(), configuration).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @AfterEach
  void tearDown() throws IOException {
    client.close();
  }

  @Test
  void logging_out_invalidates_the_session_and_redirects_to_login() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<?> response = client.exchange(logoutRequest(sessionCookie));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).isEqualTo("/login");
    assertThat(statusOf(protectedRequest(sessionCookie)))
        .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(response);
  }

  private HttpRequest<?> logoutRequest(String sessionCookie) {
    return HttpRequest.POST("/logout", Map.of())
        .contentType(MediaType.APPLICATION_JSON_TYPE)
        .header(HttpHeaders.COOKIE, sessionCookie);
  }

  private HttpRequest<?> protectedRequest(String sessionCookie) {
    return HttpRequest.GET("/api/data")
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .header(HttpHeaders.COOKIE, sessionCookie);
  }

  private int statusOf(HttpRequest<?> request) {
    try {
      return client.exchange(request).getStatus().getCode();
    } catch (HttpClientResponseException e) {
      return e.getStatus().getCode();
    }
  }
}
