package gal.conxugal.application.http.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class AcceptHeaderRejectionTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  @Test
  void xhr_shaped_request_without_session_is_rejected_not_redirected() {
    Response response =
        given()
            .baseUri(embeddedServer.getURL().toString())
            .redirects().follow(false)
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .when()
            .get("/api/data");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void browser_navigation_without_session_is_redirected_to_login() {
    Response response =
        given()
            .baseUri(embeddedServer.getURL().toString())
            .redirects().follow(false)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML)
        .when()
            .get("/api/data");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/login");
  }
}
