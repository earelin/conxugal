package gal.conxugal.application.http.error.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpStatus;
import io.restassured.response.Response;

/**
 * The RFC 9457 envelope every refusal in the API shares, asserted in one place so a caller
 * only has to name what makes its own refusal different.
 *
 * <p>The body's {@code status} is checked as a number rather than left to the response code
 * alone. The two can disagree: a handler built on an enum without a serializer writes the
 * status by name, so the body says {@code "CONFLICT"} where the contract declares an integer,
 * while the response code stays correct and nothing notices.
 */
public final class ProblemAssertions {

  private static final String PROBLEM_JSON = "application/problem+json";

  private ProblemAssertions() {
  }

  /**
   * Asserts the response is a problem document of {@code status} carrying {@code problemType}
   * — the status code, the content type, the body's own {@code status}, and the {@code type}
   * that tells two refusals of the same status apart.
   */
  public static void assertProblem(Response response, HttpStatus status, String problemType) {
    response.then()
        .statusCode(status.getCode())
        .contentType(PROBLEM_JSON);
    assertThat(response.jsonPath().getString("type")).isEqualTo(problemType);
    assertThat(response.jsonPath().getInt("status")).isEqualTo(status.getCode());
  }
}
