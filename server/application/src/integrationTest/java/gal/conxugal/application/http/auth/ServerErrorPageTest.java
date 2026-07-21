package gal.conxugal.application.http.auth;

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
class ServerErrorPageTest extends AuthenticationTestSupport {

  @BeforeEach
  void setUp() {
    seedUser(TestUserFactory.normalUser());
  }

  @Test
  void non_api_failure_renders_the_styled_error_page(RequestSpecification spec) {
    Response response =
        given(spec)
        .when()
            .get("/test-support/boom");

    response
        .then()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    assertThat(response.getContentType()).contains(MediaType.TEXT_HTML);
    String body = response.getBody().asString();
    assertThat(body).contains("Algo foi mal");
    assertThat(body).doesNotContain("boom - sensitive detail");
  }

  @Test
  void api_failure_returns_problem_details_json_body(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/test-support/boom");

    response
        .then()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    assertThat(response.getContentType()).contains("application/problem+json");
    String body = response.getBody().asString();
    assertThat(body).contains("\"type\":\"urn:conxugal:problem-type:internal-server-error\"");
    assertThat(body).contains("\"status\":500");
    assertThat(body).contains("\"instance\":\"/api/test-support/boom\"");
    assertThat(body).doesNotContain("Algo foi mal");
    assertThat(body).doesNotContain("boom - sensitive detail");
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
