package gal.conxugal.application.http.spa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
class SpaHistoryFallbackTest extends AuthenticationTestSupport {

  private static final String SPA_SHELL_MARKER = "<div id=\"root\">";

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;
  private String sessionCookie;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
    sessionCookie = login("user@example.com", "user-password");
  }

  @Test
  void known_client_side_route_falls_back_to_the_spa_shell() {
    HttpResponse<String> response = getWithSession("/acerca");

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(MediaType.TEXT_HTML);
    assertThat(response.body()).contains(SPA_SHELL_MARKER);
  }

  @Test
  void unknown_client_side_route_also_falls_back_to_the_spa_shell() {
    HttpResponse<String> response = getWithSession("/rota-que-non-existe");

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains(MediaType.TEXT_HTML);
    assertThat(response.body()).contains(SPA_SHELL_MARKER);
  }

  @Test
  void unmatched_api_path_returns_plain_not_found_never_the_spa_shell() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/rota-que-non-existe").header(HttpHeaders.COOKIE, sessionCookie);

    HttpClientResponseException error =
        catchThrowableOfType(
            HttpClientResponseException.class, () -> client.exchange(request, String.class));

    assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    assertThat(error.getResponse().getBody(String.class).orElse(""))
        .doesNotContain(SPA_SHELL_MARKER);
  }

  @Test
  void non_get_request_to_an_unmatched_path_is_not_served_the_spa_shell() {
    HttpRequest<?> request =
        HttpRequest.POST("/rota-que-non-existe", "{}")
            .contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.COOKIE, sessionCookie);

    HttpClientResponseException error =
        catchThrowableOfType(
            HttpClientResponseException.class, () -> client.exchange(request, String.class));

    assertThat(error.getStatus().getCode()).isNotEqualTo(HttpStatus.OK.getCode());
    assertThat(error.getResponse().getBody(String.class).orElse(""))
        .doesNotContain(SPA_SHELL_MARKER);
  }

  private HttpResponse<String> getWithSession(String path) {
    return client.exchange(
        HttpRequest.GET(path).header(HttpHeaders.COOKIE, sessionCookie), String.class);
  }

  private String login(String email, String password) {
    HttpResponse<?> response =
        client.exchange(
            HttpRequest.POST("/login", new UsernamePasswordCredentials(email, password))
                .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(response);
  }
}
