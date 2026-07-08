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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class ForbiddenPageTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void set_up() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void authenticated_visitor_sees_the_forbidden_page() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<String> response = client.exchange(
        HttpRequest.GET("/forbidden").header(HttpHeaders.COOKIE, sessionCookie), String.class);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body()).contains("Non tes permisos para acceder a esta páxina.");
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return response.getHeaders().get(HttpHeaders.SET_COOKIE).split(";", 2)[0];
  }
}
