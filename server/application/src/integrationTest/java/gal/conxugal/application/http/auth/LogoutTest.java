package gal.conxugal.application.http.auth;

import static io.restassured.RestAssured.given;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
class LogoutTest extends AuthenticationTestSupport {

  @BeforeEach
  void setUp() {
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void logging_out_invalidates_the_session_and_redirects_to_login(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    logoutRequest(spec, sessionCookie)
        .then()
            .statusCode(HttpStatus.SEE_OTHER.getCode())
            .header(HttpHeaders.LOCATION, "/login");

    protectedRequest(spec, sessionCookie)
        .then()
            .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String login(RequestSpecification spec, String email, String password) {
    Response response =
        given()
            .spec(spec)
            .redirects().follow(false)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }

  private Response logoutRequest(RequestSpecification spec, String sessionCookie) {
    return given()
        .spec(spec)
        .redirects().follow(false)
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{}")
    .when()
        .post("/logout");
  }

  private Response protectedRequest(RequestSpecification spec, String sessionCookie) {
    return given()
        .spec(spec)
        .redirects().follow(false)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/data");
  }
}
