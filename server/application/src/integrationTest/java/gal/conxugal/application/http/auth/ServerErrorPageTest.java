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
class ServerErrorPageTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void non_api_failure_renders_the_styled_error_page() {
    HttpResponse<?> response = failingResponseOf(HttpRequest.GET("/test-support/boom"));

    assertThat(response.getStatus().getCode())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    assertThat(response.getContentType()).hasValueSatisfying(
        contentType -> assertThat(contentType.toString()).contains(MediaType.TEXT_HTML));
    String body = response.getBody(String.class).orElseThrow();
    assertThat(body).contains("Algo foi mal");
    assertThat(body).doesNotContain("boom - sensitive detail");
  }

  @Test
  void api_failure_returns_problem_details_json_body() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<?> response = failingResponseOf(
        HttpRequest.GET("/api/test-support/boom").header(HttpHeaders.COOKIE, sessionCookie));

    assertThat(response.getStatus().getCode())
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    assertThat(response.getContentType()).hasValueSatisfying(
        contentType -> assertThat(contentType.toString()).contains("application/problem+json"));
    String body = response.getBody(String.class).orElseThrow();
    assertThat(body).contains("\"type\":\"urn:conxugal:problem-type:internal-server-error\"");
    assertThat(body).contains("\"status\":500");
    assertThat(body).contains("\"instance\":\"/api/test-support/boom\"");
    assertThat(body).doesNotContain("Algo foi mal");
    assertThat(body).doesNotContain("boom - sensitive detail");
  }

  private HttpResponse<?> failingResponseOf(HttpRequest<?> request) {
    try {
      return client.exchange(request, String.class);
    } catch (HttpClientResponseException e) {
      return e.getResponse();
    }
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(response);
  }
}
