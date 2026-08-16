package gal.conxugal.application.rest.request;

import static gal.conxugal.application.rest.request.Refusals.refuseUnknownParameters;
import static gal.conxugal.application.rest.request.Refusals.refused;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.zalando.problem.ThrowableProblem;

class RefusalsTest {

  private static final Set<String> ACCEPTED = Set.of("year", "sort");

  @Test
  void refusal_carries_the_status_and_the_reason_it_was_given() {
    ThrowableProblem problem = refused("sort must be property,direction");

    assertThat(problem.getStatus().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    assertThat(problem.getTitle()).isEqualTo("Bad Request");
    assertThat(problem.getDetail()).isEqualTo("sort must be property,direction");
  }

  @Test
  void accepted_parameters_pass_through_untouched() {
    assertThatCode(() -> refuseUnknownParameters(get("/api/thing?year=2025&sort=amount,asc"),
        ACCEPTED))
        .doesNotThrowAnyException();
  }

  @Test
  void request_carrying_no_parameters_at_all_passes_through() {
    assertThatCode(() -> refuseUnknownParameters(get("/api/thing"), ACCEPTED))
        .doesNotThrowAnyException();
  }

  @Test
  void unknown_parameter_is_refused_and_named() {
    assertThatThrownBy(() -> refuseUnknownParameters(get("/api/thing?year=2025&order=amount"),
        ACCEPTED))
        .isInstanceOf(ThrowableProblem.class)
        .hasMessageContaining("no such query parameter: order");
  }

  // One name, chosen deterministically, so the same wrong request always answers the same way
  // rather than naming whichever parameter the container happened to iterate first.
  @Test
  void several_unknown_parameters_are_answered_by_naming_the_first_of_them() {
    assertThatThrownBy(() -> refuseUnknownParameters(get("/api/thing?zulu=1&alpha=2&mike=3"),
        ACCEPTED))
        .hasMessageContaining("no such query parameter: alpha")
        .hasMessageNotContainingAny("zulu", "mike");
  }

  // The boundary itself, which only an assertion at this level can pin: forty characters are
  // repeated whole, and the forty-first is what turns the name into an abbreviation.
  @Test
  void name_of_exactly_forty_characters_is_repeated_whole() {
    String name = "a".repeat(40);

    assertThatThrownBy(() -> refuseUnknownParameters(get("/api/thing?" + name + "=1"), ACCEPTED))
        .hasMessageEndingWith(name);
  }

  @Test
  void longer_name_is_cut_to_forty_characters_and_marked_as_cut() {
    assertThatThrownBy(() -> refuseUnknownParameters(get("/api/thing?" + "a".repeat(41) + "=1"),
        ACCEPTED))
        .hasMessageEndingWith("a".repeat(40) + "…");
  }

  // A percent-encoded newline is a name a caller can send, and the problem document declares its
  // detail a single line — so a refusal echoing one verbatim would answer with a body the
  // contract rejects.
  @Test
  void control_characters_in_the_name_are_flattened_to_one_space() {
    assertThatThrownBy(() -> refuseUnknownParameters(get("/api/thing?a%0A%09b=1"), ACCEPTED))
        .hasMessageEndingWith("a b");
  }

  private static HttpRequest<?> get(String uri) {
    return HttpRequest.GET(uri);
  }
}
