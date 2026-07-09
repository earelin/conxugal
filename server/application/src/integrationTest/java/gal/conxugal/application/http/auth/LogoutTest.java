package gal.conxugal.application.http.auth;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class LogoutTest extends AuthenticationTestSupport {

  @Inject
  EmbeddedServer embeddedServer;

  @BeforeEach
  void setUp() {
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void logging_out_invalidates_the_session_and_redirects_to_login() {
    String sessionCookie = login("user@example.com", "user-password");

    Response response = logoutRequest(sessionCookie);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER.getCode());
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/login");
    assertThat(protectedRequest(sessionCookie).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(String email, String password) {
    Response response =
        given()
            .baseUri(embeddedServer.getURL().toString())
            .redirects().follow(false)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }

  private Response logoutRequest(String sessionCookie) {
    return given()
        .baseUri(embeddedServer.getURL().toString())
        .redirects().follow(false)
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{}")
    .when()
        .post("/logout");
  }

  private Response protectedRequest(String sessionCookie) {
    return given()
        .baseUri(embeddedServer.getURL().toString())
        .redirects().follow(false)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/data");
  }
}
