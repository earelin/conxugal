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
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class ForbiddenPageTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  @Inject
  CsrfConfiguration csrfConfiguration;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void authenticated_visitor_sees_the_forbidden_page() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<String> response = client.exchange(
        HttpRequest.GET("/forbidden").header(HttpHeaders.COOKIE, sessionCookie), String.class);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body()).contains("Acceso denegado");
    assertThat(response.body())
        .contains("A túa conta non ten permisos para acceder a esta área.");
    assertThat(response.body()).contains("name=\"csrfToken\"");
  }

  @Test
  void submitting_the_pechar_sesion_form_logs_out_the_session() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<String> forbiddenPage = client.exchange(
        HttpRequest.GET("/forbidden").header(HttpHeaders.COOKIE, sessionCookie), String.class);
    String csrfCookieHeader = csrfCookieHeaderOf(forbiddenPage);
    String csrfToken = csrfCookieHeader.substring(csrfCookieHeader.indexOf('=') + 1);

    Map<String, String> form = new HashMap<>();
    form.put("csrfToken", csrfToken);

    HttpResponse<?> logoutResponse = client.exchange(HttpRequest.POST("/logout", form)
        .header(HttpHeaders.COOKIE, sessionCookie + "; " + csrfCookieHeader)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    assertThat(logoutResponse.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

    assertThat(statusOfForbiddenPage(sessionCookie)).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private int statusOfForbiddenPage(String sessionCookie) {
    HttpRequest<?> request =
        HttpRequest.GET("/forbidden").header(HttpHeaders.COOKIE, sessionCookie);
    try {
      return client.exchange(request).getStatus().getCode();
    } catch (HttpClientResponseException e) {
      return e.getStatus().getCode();
    }
  }

  private String csrfCookieHeaderOf(HttpResponse<?> response) {
    String cookieName = csrfConfiguration.getCookieName();
    return response.getHeaders().getAll(HttpHeaders.SET_COOKIE).stream()
        .filter(header -> header.startsWith(cookieName + "="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(response);
  }
}
