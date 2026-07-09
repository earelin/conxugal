package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class AcceptHeaderRejectionTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  private BlockingHttpClient client;

  @BeforeEach
  void setUp() {
    HttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
    configuration.setFollowRedirects(false);
    client = HttpClient.create(embeddedServer.getURL(), configuration).toBlocking();
  }

  @Test
  void xhr_shaped_request_without_session_is_rejected_not_redirected() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/data").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);

    assertThat(statusOf(request)).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void browser_navigation_without_session_is_redirected_to_login() {
    HttpRequest<?> request =
        HttpRequest.GET("/api/data").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML);

    HttpResponse<?> response = client.exchange(request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeaders().get(HttpHeaders.LOCATION)).isEqualTo("/login");
  }

  private int statusOf(HttpRequest<?> request) {
    try {
      return client.exchange(request).getStatus().getCode();
    } catch (HttpClientResponseException e) {
      return e.getStatus().getCode();
    }
  }
}
