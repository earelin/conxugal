package gal.conxugal.application.http.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.metrics.HttpRequestCounter;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class HttpRequestCounterIntegrationTest extends AuthenticationTestSupport {

  @Inject
  private HttpRequestCounter counter;

  @BeforeEach
  void setUp() {
    seedUser(new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER));
  }

  @Test
  void successful_request_moves_request_count_but_not_error_count(RequestSpecification spec) {
    RuntimeMetrics.Http before = counter.snapshot();

    given(spec)
    .when()
        .get("/login")
    .then()
        .statusCode(HttpStatus.OK.getCode());

    RuntimeMetrics.Http after = counter.snapshot();
    assertThat(after.requestCount()).isEqualTo(before.requestCount() + 1);
    assertThat(after.errorCount()).isEqualTo(before.errorCount());
  }

  @Test
  void thrown_error_moves_both_request_and_error_count(RequestSpecification spec) {
    RuntimeMetrics.Http before = counter.snapshot();

    given(spec)
    .when()
        .get("/test-support/boom")
    .then()
        .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.getCode());

    RuntimeMetrics.Http after = counter.snapshot();
    assertThat(after.requestCount()).isEqualTo(before.requestCount() + 1);
    assertThat(after.errorCount()).isEqualTo(before.errorCount() + 1);
  }

  @Test
  void non_throwing_not_found_response_moves_both_request_and_error_count(
      RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");
    RuntimeMetrics.Http before = counter.snapshot();

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/rota-que-non-existe")
    .then()
        .statusCode(HttpStatus.NOT_FOUND.getCode());

    RuntimeMetrics.Http after = counter.snapshot();
    assertThat(after.requestCount()).isEqualTo(before.requestCount() + 1);
    assertThat(after.errorCount()).isEqualTo(before.errorCount() + 1);
  }

  @Test
  void security_denied_request_moves_both_request_and_error_count(RequestSpecification spec) {
    RuntimeMetrics.Http before = counter.snapshot();

    given(spec)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
    .when()
        .get("/api/data")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());

    RuntimeMetrics.Http after = counter.snapshot();
    assertThat(after.requestCount()).isEqualTo(before.requestCount() + 1);
    assertThat(after.errorCount()).isEqualTo(before.errorCount() + 1);
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
