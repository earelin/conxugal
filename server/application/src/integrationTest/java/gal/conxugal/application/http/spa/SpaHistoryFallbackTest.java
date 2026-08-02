package gal.conxugal.application.http.spa;

import static gal.conxugal.application.http.error.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class SpaHistoryFallbackTest extends AuthenticationTestSupport {

  private static final String SPA_SHELL_MARKER = "<div id=\"root\">";

  @BeforeEach
  void setUp() {
    seedUser(TestUserFactory.normalUser());
  }

  @Test
  void known_client_side_route_falls_back_to_the_spa_shell(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .accept(MediaType.TEXT_HTML)
        .when()
            .get("/acerca");

    response
        .then()
            .statusCode(HttpStatus.OK.getCode());
    assertThat(response.getContentType()).contains(MediaType.TEXT_HTML);
    assertThat(response.getBody().asString()).contains(SPA_SHELL_MARKER);
  }

  @Test
  void unknown_client_side_route_also_falls_back_to_the_spa_shell(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/rota-que-non-existe");

    response
        .then()
            .statusCode(HttpStatus.OK.getCode());
    assertThat(response.getContentType()).contains(MediaType.TEXT_HTML);
    assertThat(response.getBody().asString()).contains(SPA_SHELL_MARKER);
  }

  @Test
  void missing_hashed_asset_returns_plain_not_found_never_the_spa_shell(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/assets/index-doesnotexist.js");

    response
        .then()
            .statusCode(HttpStatus.NOT_FOUND.getCode());
    assertThat(response.getBody().asString()).doesNotContain(SPA_SHELL_MARKER);
  }

  @Test
  void missing_root_asset_with_extension_returns_not_found_never_the_spa_shell(
      RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/does-not-exist.svg");

    response
        .then()
            .statusCode(HttpStatus.NOT_FOUND.getCode());
    assertThat(response.getBody().asString()).doesNotContain(SPA_SHELL_MARKER);
  }

  @Test
  void anonymous_request_to_public_asset_namespace_is_not_served_the_spa_shell(
      RequestSpecification spec) {
    Response response =
        given(spec)
        .when()
            .get("/assets/static-pages/does-not-exist");

    response
        .then()
            .statusCode(HttpStatus.NOT_FOUND.getCode());
    assertThat(response.getBody().asString()).doesNotContain(SPA_SHELL_MARKER);
  }

  @Test
  void unmatched_api_path_returns_problem_json_not_found_never_the_spa_shell(
      RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .accept(MediaType.APPLICATION_JSON)
        .when()
            .get("/api/rota-que-non-existe");

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:not-found");
    String body = response.getBody().asString();
    assertThat(body).contains("\"instance\":\"/api/rota-que-non-existe\"");
    assertThat(body).doesNotContain(SPA_SHELL_MARKER);
  }

  @Test
  void non_get_request_to_an_unmatched_path_is_not_served_the_spa_shell(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{}")
        .when()
            .post("/rota-que-non-existe");

    response
        .then()
            .statusCode(HttpStatus.NOT_FOUND.getCode());
    assertThat(response.getBody().asString()).doesNotContain(SPA_SHELL_MARKER);
  }

  private String login(RequestSpecification spec, String email, String password) {
    Response response =
        given(spec)
            .body("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }
}
