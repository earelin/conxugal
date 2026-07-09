package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class LoginPageTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  @Inject
  CsrfConfiguration csrfConfiguration;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    HttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
    configuration.setFollowRedirects(false);
    client = HttpClient.create(embeddedServer.getURL(), configuration).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void anonymous_visitor_sees_the_login_form() {
    HttpResponse<String> response = client.exchange(HttpRequest.GET("/login"), String.class);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    String body = response.body();
    assertThat(body).contains("action=\"/login\"");
    assertThat(body).contains("name=\"username\"");
    assertThat(body).contains("name=\"password\"");
    assertThat(body).contains("name=\"csrfToken\"");
  }

  @Test
  void failed_login_query_param_shows_single_generic_error() {
    String withError = client.exchange(HttpRequest.GET("/login?error=true"), String.class).body();
    String withoutError = client.exchange(HttpRequest.GET("/login"), String.class).body();

    assertThat(withError).contains("As credenciais introducidas non son correctas.");
    assertThat(withoutError).doesNotContain("As credenciais introducidas non son correctas.");
  }

  @Test
  void already_authenticated_visitor_of_login_is_sent_to_home() {
    String sessionCookie = login("user@example.com", "user-password");

    HttpResponse<?> response = client.exchange(
        HttpRequest.GET("/login").header(HttpHeaders.COOKIE, sessionCookie));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).isEqualTo("/");
  }

  @Test
  void submitting_the_html_form_with_valid_credentials_redirects_to_home() {
    HttpResponse<?> response =
        client.exchange(formLoginRequest("user@example.com", "user-password"));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).isEqualTo("/");
  }

  @Test
  void submitting_the_html_form_with_an_invalid_password_redirects_to_login_with_error() {
    HttpResponse<?> response =
        client.exchange(formLoginRequest("user@example.com", "wrong-password"));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).isEqualTo("/login?error=true");
  }

  @Test
  void submitting_the_html_form_with_tampered_csrf_token_is_rejected() {
    HttpRequest<?> request =
        formLoginRequestWithTamperedCsrfToken("user@example.com", "user-password");

    assertThatThrownBy(() -> client.exchange(request))
        .isInstanceOf(HttpClientResponseException.class)
        .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(String email, String password) {
    HttpResponse<?> response = client.exchange(HttpRequest
        .POST("/login", new UsernamePasswordCredentials(email, password))
        .contentType(MediaType.APPLICATION_JSON_TYPE));
    return sessionCookieOf(response);
  }

  private HttpRequest<?> formLoginRequest(String email, String password) {
    String csrfCookieHeader = csrfCookieHeaderOf(client.exchange(HttpRequest.GET("/login")));
    String csrfToken = csrfCookieHeader.substring(csrfCookieHeader.indexOf('=') + 1);

    return formLoginRequest(email, password, csrfCookieHeader, csrfToken);
  }

  private HttpRequest<?> formLoginRequest(
      String email, String password, String csrfCookieHeader, String csrfToken) {
    Map<String, String> form = new HashMap<>();
    form.put("username", email);
    form.put("password", password);
    form.put("csrfToken", csrfToken);

    return HttpRequest.POST("/login", form)
        .header(HttpHeaders.COOKIE, csrfCookieHeader)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
  }

  private HttpRequest<?> formLoginRequestWithTamperedCsrfToken(String email, String password) {
    String csrfCookieHeader = csrfCookieHeaderOf(client.exchange(HttpRequest.GET("/login")));

    return formLoginRequest(email, password, csrfCookieHeader, "tampered-token");
  }

  private String csrfCookieHeaderOf(HttpResponse<?> loginPage) {
    String cookieName = csrfConfiguration.getCookieName();
    return loginPage.getHeaders().getAll(HttpHeaders.SET_COOKIE).stream()
        .filter(header -> header.startsWith(cookieName + "="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }
}
