package gal.conxugal.application.http.error;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

/**
 * The refusals Micronaut raises before any handler of ours runs. They share the RFC 9457
 * envelope with the application's own refusals, so they carry the same required properties —
 * {@code title} above all, which the framework leaves off whatever raised the failure gave it
 * nothing to name itself with.
 */
@MicronautTest
class FrameworkRefusalProblemTest extends AuthenticationTestSupport {

  @Test
  void unauthenticated_request_is_refused_with_complete_problem_document(
      RequestSpecification spec) {
    assertProblem(
        given(spec)
        .when()
            .get("/api/data"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasTitle("Unauthorized");
  }

  @Test
  void forbidden_request_is_refused_with_complete_problem_document(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    assertProblem(
        given(spec)
            .header("Cookie", sessionCookie)
        .when()
            .get("/api/admin/reports"))
        .hasStatus(HttpStatus.FORBIDDEN)
        .hasTitle("Forbidden");
  }
}
