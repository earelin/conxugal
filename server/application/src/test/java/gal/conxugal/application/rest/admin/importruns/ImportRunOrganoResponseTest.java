package gal.conxugal.application.rest.admin.importruns;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.organo.OrganoId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportRunOrganoResponseTest {

  private static final OrganoId SERGAS = new OrganoId(UUID.randomUUID());
  private static final int LONGEST_REASON = 500;

  @Test
  void carries_the_organo_identity_as_bare_uuid_and_the_state_by_name() {
    ImportRunOrganoResponse response =
        ImportRunOrganoResponse.of(coverageWith(ImportRunOrganoState.SUCCEEDED, null));

    assertThat(response.organoId()).isEqualTo(SERGAS.value());
    assertThat(response.state()).isEqualTo("SUCCEEDED");
  }

  @Test
  void keeps_the_reason_that_already_reads_as_one_bounded_line() {
    ImportRunOrganoResponse response = responseWithReason("The source became unreachable");

    assertThat(response.failureReason()).isEqualTo("The source became unreachable");
  }

  // A failure caught mid-walk arrives shaped like whatever went wrong, and a wrapped statement
  // spanning several lines is the ordinary case.
  @Test
  void collapses_the_reason_that_spans_several_lines_into_one() {
    ImportRunOrganoResponse response =
        responseWithReason("Statement failed:\n  SELECT 1\r\n\tfrom nowhere  ");

    assertThat(response.failureReason()).isEqualTo("Statement failed: SELECT 1 from nowhere");
  }

  // The producer truncates at the bound and appends an ellipsis, which lands one character past
  // it: the reason a client is handed has to hold the bound the contract declares.
  @Test
  void truncates_the_reason_longer_than_the_bound_to_the_bound() {
    ImportRunOrganoResponse response = responseWithReason("x".repeat(LONGEST_REASON + 40));

    assertThat(response.failureReason())
        .hasSize(LONGEST_REASON)
        .endsWith("…");
  }

  @Test
  void answers_with_no_reason_when_there_is_none() {
    ImportRunOrganoResponse response =
        ImportRunOrganoResponse.of(coverageWith(ImportRunOrganoState.SUCCEEDED, null));

    assertThat(response.failureReason()).isNull();
  }

  // A caught failure can carry an empty message, which is not null and so reaches here as one.
  @Test
  void answers_with_no_reason_when_the_recorded_one_says_nothing() {
    ImportRunOrganoResponse response = responseWithReason("  \n\t ");

    assertThat(response.failureReason()).isNull();
  }

  private static ImportRunOrganoResponse responseWithReason(String failureReason) {
    return ImportRunOrganoResponse.of(
        coverageWith(ImportRunOrganoState.FAILED, failureReason));
  }

  private static ImportRunOrganoCoverage coverageWith(
      ImportRunOrganoState state, String failureReason) {
    return new ImportRunOrganoCoverage(SERGAS, state, 1204, 96, failureReason);
  }
}
