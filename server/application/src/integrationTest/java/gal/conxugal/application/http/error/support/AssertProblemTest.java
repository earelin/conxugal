package gal.conxugal.application.http.error.support;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micronaut.http.HttpStatus;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

// A plain JUnit test with no Micronaut context, living in this source set only because the
// class it covers does — the integration suite is the one that can see it. What matters here
// is the utility failing when it should: every assertion it makes is the reason some other
// suite would catch a defect, so each test below drives a body that ought to be rejected and
// asserts that it is.
class AssertProblemTest {

  private static final String PROBLEM_JSON = "application/problem+json";

  private static final String CYCLE =
      """
      {"type":"urn:conxugal:problem-type:termo-cycle","title":"Term Cycle","status":409,\
      "detail":"Cannot move term A under itself"}\
      """;

  @Test
  void accepts_well_formed_problem_document() {
    Response response = problemResponse(CYCLE);
    when(response.getStatusCode()).thenReturn(HttpStatus.CONFLICT.getCode());

    assertThatCode(() ->
        assertProblem(response)
            .hasStatus(HttpStatus.CONFLICT)
            .hasType("urn:conxugal:problem-type:termo-cycle")
            .hasTitle("Term Cycle")
            .hasDetail("Cannot move term A under itself"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejects_response_that_is_not_problem_document() {
    Response response = mock(Response.class);
    when(response.getContentType()).thenReturn("application/json");

    assertThatThrownBy(() -> assertProblem(response))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem content type");
  }

  // The defect the shared assertion exists to catch: a handler whose status is written by
  // name serialises "CONFLICT" where the schema declares an integer, while the response code
  // stays 409 and every status-code assertion still passes.
  @Test
  void rejects_status_written_as_name_rather_than_number() {
    Response response = problemResponse(
        """
        {"type":"urn:conxugal:problem-type:termo-cycle","title":"Term Cycle",\
        "status":"CONFLICT"}\
        """);

    assertThatThrownBy(() -> assertProblem(response))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Error schema declares an integer");
  }

  @Test
  void rejects_document_missing_required_property() {
    Response response = problemResponse(
        """
        {"title":"Term Cycle","status":409}\
        """);

    assertThatThrownBy(() -> assertProblem(response))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem type");
  }

  @Test
  void rejects_response_code_that_is_not_expected_status() {
    Response response = problemResponse(CYCLE);
    when(response.getStatusCode()).thenReturn(HttpStatus.OK.getCode());

    assertThatThrownBy(() -> assertProblem(response).hasStatus(HttpStatus.CONFLICT))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("response status");
  }

  // The half a response-code assertion alone cannot see: the body repeating a status that is
  // not the one the response carried.
  @Test
  void rejects_body_status_that_disagrees_with_response_code() {
    Response response = problemResponse(
        """
        {"type":"urn:conxugal:problem-type:termo-cycle","title":"Term Cycle","status":404}\
        """);
    when(response.getStatusCode()).thenReturn(HttpStatus.CONFLICT.getCode());

    assertThatThrownBy(() -> assertProblem(response).hasStatus(HttpStatus.CONFLICT))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem status");
  }

  @Test
  void rejects_type_that_does_not_match() {
    Response response = problemResponse(CYCLE);

    assertThatThrownBy(() ->
        assertProblem(response).hasType("urn:conxugal:problem-type:termo-has-children"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem type");
  }

  @Test
  void rejects_title_that_does_not_match() {
    Response response = problemResponse(CYCLE);

    assertThatThrownBy(() -> assertProblem(response).hasTitle("Conflict"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem title");
  }

  @Test
  void rejects_detail_that_does_not_match() {
    Response response = problemResponse(CYCLE);

    assertThatThrownBy(() -> assertProblem(response).hasDetail("Something else"))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem detail");
  }

  @Test
  void checks_instance_that_generic_failure_carries() {
    Response response = problemResponse(
        """
        {"type":"urn:conxugal:problem-type:not-found","title":"Not Found","status":404,\
        "instance":"/api/rota-que-non-existe"}\
        """);

    assertThatCode(() -> assertProblem(response).hasInstance("/api/rota-que-non-existe"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertProblem(response).hasNoInstance())
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("problem instance");
  }

  @Test
  void treats_absent_instance_as_no_instance() {
    Response response = problemResponse(CYCLE);

    assertThatCode(() -> assertProblem(response).hasNoInstance())
        .doesNotThrowAnyException();
  }

  @Test
  void returns_the_same_assertion_so_checks_can_be_chained() {
    Response response = problemResponse(CYCLE);

    AssertProblem assertion = assertProblem(response);

    assertThat(assertion.hasTitle("Term Cycle")).isSameAs(assertion);
  }

  private static Response problemResponse(String json) {
    Response response = mock(Response.class);
    when(response.getContentType()).thenReturn(PROBLEM_JSON);
    when(response.jsonPath()).thenReturn(new JsonPath(json));
    return response;
  }
}
