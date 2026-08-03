package gal.conxugal.application.http.error.support;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpStatus;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

/**
 * A fluent assertion over the RFC 9457 envelope every refusal in the API shares, so a caller
 * names only what makes its own refusal different:
 *
 * <pre>{@code
 * assertProblem(response)
 *     .hasStatus(HttpStatus.CONFLICT)
 *     .hasType("urn:conxugal:problem-type:termo-cycle");
 * }</pre>
 *
 * <p>Entering the chain already asserts what makes a response a problem document at all: the
 * content type, and the three properties the {@code Error} schema marks required. That
 * includes {@code status} being a <em>number</em> — the property most easily got wrong, since
 * a handler built on an enum without a serializer writes it by name, leaving the body saying
 * {@code "CONFLICT"} where the contract declares an integer while the response code stays
 * correct and nothing notices.
 */
public final class AssertProblem {

  private static final String PROBLEM_JSON = "application/problem+json";

  private final Response response;
  private final JsonPath body;

  private AssertProblem(Response response) {
    this.response = response;
    assertThat(response.getContentType())
        .as("problem content type")
        .contains(PROBLEM_JSON);
    this.body = response.jsonPath();
    assertThat(body.getString("type")).as("problem type").isNotNull();
    assertThat(body.getString("title")).as("problem title").isNotNull();
    // get() is generic, so the target type has to be pinned for the isInstanceOf below to be
    // about the JSON value rather than about whatever the compiler inferred.
    Object status = body.get("status");
    assertThat(status)
        .as("problem status, which the Error schema declares an integer")
        .isInstanceOf(Integer.class);
  }

  /** Starts a chain, asserting the response is a problem document before anything else. */
  public static AssertProblem assertProblem(Response response) {
    return new AssertProblem(response);
  }

  /**
   * Asserts the response code and the body's own {@code status}, which the contract requires
   * to repeat it. Checking one without the other lets them drift apart unnoticed.
   */
  public AssertProblem hasStatus(HttpStatus status) {
    assertThat(response.getStatusCode()).as("response status").isEqualTo(status.getCode());
    assertThat(body.getInt("status")).as("problem status").isEqualTo(status.getCode());
    return this;
  }

  /** Asserts the {@code type} URN, which is what tells two refusals of one status apart. */
  public AssertProblem hasType(String type) {
    assertThat(body.getString("type")).as("problem type").isEqualTo(type);
    return this;
  }

  public AssertProblem hasTitle(String title) {
    assertThat(body.getString("title")).as("problem title").isEqualTo(title);
    return this;
  }

  public AssertProblem hasDetail(String detail) {
    assertThat(body.getString("detail")).as("problem detail").isEqualTo(detail);
    return this;
  }

  public AssertProblem hasInstance(String instance) {
    assertThat(body.getString("instance")).as("problem instance").isEqualTo(instance);
    return this;
  }

  /**
   * Asserts no {@code instance} is carried. The contract leaves it to the generic failures and
   * says the business-outcome problems don't set it, so this is the assertion for those.
   */
  public AssertProblem hasNoInstance() {
    assertThat(body.getString("instance")).as("problem instance").isNull();
    return this;
  }
}
