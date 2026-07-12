package gal.conxugal.application.http.auth;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

@MicronautTest
class AcceptHeaderRejectionTest extends AuthenticationTestSupport {

  @Test
  void xhr_shaped_request_without_session_is_rejected_not_redirected(RequestSpecification spec) {
    given(spec)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
    .when()
        .get("/api/data")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void browser_navigation_without_session_is_redirected_to_login(RequestSpecification spec) {
    given(spec)
        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML)
    .when()
        .get("/api/data")
    .then()
        .statusCode(HttpStatus.SEE_OTHER.getCode())
        .header(HttpHeaders.LOCATION, "/login");
  }
}
