package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class BasicAuthDisabledTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    client = HttpClient.create(embeddedServer.getURL()).toBlocking();
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void valid_credentials_sent_as_http_basic_auth_are_not_accepted() {
    HttpRequest<?> request = HttpRequest.GET("/api/data")
        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader("user@example.com", "user-password"));

    assertThatThrownBy(() -> client.exchange(request))
        .isInstanceOf(HttpClientResponseException.class)
        .extracting(exception -> ((HttpClientResponseException) exception).getStatus().getCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthorized_response_does_not_challenge_for_http_basic_auth() {
    try {
      client.exchange(HttpRequest.GET("/api/data"));
    } catch (HttpClientResponseException e) {
      assertThat(e.getResponse().getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();
      return;
    }
    throw new AssertionError("expected request to be rejected");
  }

  private String basicAuthHeader(String email, String password) {
    String credentials = email + ":" + password;
    return "Basic " + Base64.getEncoder()
        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }
}
