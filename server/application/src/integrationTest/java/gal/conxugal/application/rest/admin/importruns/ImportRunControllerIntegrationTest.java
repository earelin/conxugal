package gal.conxugal.application.rest.admin.importruns;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import gal.conxugal.domain.importrun.ImportRunOrganoState;
import gal.conxugal.domain.importrun.ImportRunReport;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The read that makes a trigger answerable: the triggers answer before the import starts, so this
// is the only place its outcome can be seen.
@MicronautTest
class ImportRunControllerIntegrationTest extends AuthenticationTestSupport {

  private static final ImportRunId RUN = new ImportRunId(UUID.randomUUID());
  private static final OrganoId SERGAS = new OrganoId(UUID.randomUUID());
  private static final OrganoId CHUAC = new OrganoId(UUID.randomUUID());
  private static final Instant STARTED_AT = Instant.parse("2026-08-13T09:14:02Z");
  private static final Instant FINISHED_AT = Instant.parse("2026-08-15T21:40:55Z");

  @Inject
  ImportRunRepository importRuns;

  @MockBean(ImportRunRepository.class)
  ImportRunRepository importRunsMock() {
    return mock(ImportRunRepository.class);
  }

  @Test
  void admin_reads_run_still_in_progress(RequestSpecification spec) {
    Response response =
        readAsAdmin(
            spec,
            report(
                ImportRunState.IN_PROGRESS,
                null,
                12,
                3,
                new ImportRunOrganoCoverage(SERGAS, ImportRunOrganoState.IN_PROGRESS, 12, 3, null),
                new ImportRunOrganoCoverage(CHUAC, ImportRunOrganoState.PENDING, 0, 0, null)));

    assertThat(response.jsonPath().getString("state")).isEqualTo("IN_PROGRESS");
    assertThat(response.jsonPath().getString("finishedAt")).isNull();
    assertThat(response.jsonPath().getList("coveredOrganos.state"))
        .containsExactly("IN_PROGRESS", "PENDING");
  }

  // #30: a run carrying on past an Órgano that failed is neither a bare success nor a bare
  // failure, and the covered Órganos are what name the one that failed.
  @Test
  void run_with_one_failed_organo_reads_as_partially_succeeded_and_names_it(
      RequestSpecification spec) {
    Response response =
        readAsAdmin(
            spec,
            report(
                ImportRunState.PARTIALLY_SUCCEEDED,
                FINISHED_AT,
                1204,
                96,
                new ImportRunOrganoCoverage(SERGAS, ImportRunOrganoState.SUCCEEDED, 1204, 96, null),
                new ImportRunOrganoCoverage(
                    CHUAC, ImportRunOrganoState.FAILED, 0, 0, "The source became unreachable")));

    assertThat(response.jsonPath().getString("state")).isEqualTo("PARTIALLY_SUCCEEDED");
    assertThat(response.jsonPath().getInt("added")).isEqualTo(1204);
    assertThat(response.jsonPath().getInt("refreshed")).isEqualTo(96);
    assertThat(response.jsonPath().getString("coveredOrganos[1].organoId"))
        .isEqualTo(CHUAC.value().toString());
    assertThat(response.jsonPath().getString("coveredOrganos[1].state")).isEqualTo("FAILED");
    assertThat(response.jsonPath().getString("coveredOrganos[1].failureReason"))
        .isEqualTo("The source became unreachable");
  }

  // The run an administrator actually goes looking at: a multi-day import whose process died.
  // Reporting it as still in progress would leave the one question this read exists to answer
  // unanswered, and the counts it reached before dying stand.
  @Test
  void run_past_the_abandonment_bound_reads_as_abandoned_keeping_its_counts(
      RequestSpecification spec) {
    Response response =
        readAsAdmin(
            spec,
            report(
                ImportRunState.ABANDONED,
                null,
                840,
                17,
                new ImportRunOrganoCoverage(SERGAS, ImportRunOrganoState.IN_PROGRESS, 840, 17,
                    null)));

    assertThat(response.jsonPath().getString("state")).isEqualTo("ABANDONED");
    assertThat(response.jsonPath().getInt("added")).isEqualTo(840);
    assertThat(response.jsonPath().getInt("refreshed")).isEqualTo(17);
  }

  // The wrappers stop at this boundary, so the id a trigger returned is the one this read
  // accepts and answers with — never a {"value": …} around it.
  @Test
  void every_identifier_on_the_wire_is_the_bare_uuid(RequestSpecification spec) {
    Response response =
        readAsAdmin(
            spec,
            report(
                ImportRunState.SUCCEEDED,
                FINISHED_AT,
                5,
                0,
                new ImportRunOrganoCoverage(SERGAS, ImportRunOrganoState.SUCCEEDED, 5, 0, null)));

    assertThat(response.jsonPath().getString("id")).isEqualTo(RUN.value().toString());
    assertThat(response.jsonPath().getString("coveredOrganos[0].organoId"))
        .isEqualTo(SERGAS.value().toString());
    assertThat(response.asString()).doesNotContain("\"value\"");
  }

  @Test
  void unknown_run_is_import_run_not_found(RequestSpecification spec) {
    when(importRuns.findRun(RUN)).thenReturn(Optional.empty());
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(readOf(RUN));

    assertProblem(response)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:import-run-not-found");
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get(readOf(RUN))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get(readOf(RUN))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private Response readAsAdmin(RequestSpecification spec, ImportRunReport report) {
    when(importRuns.findRun(RUN)).thenReturn(Optional.of(report));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(readOf(RUN));

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }

  private static ImportRunReport report(ImportRunState state, Instant finishedAt, int added,
      int refreshed, ImportRunOrganoCoverage... coveredOrganos) {
    return new ImportRunReport(RUN, Importer.CONTRATOS_MENORES, state, STARTED_AT, finishedAt,
        added, refreshed, List.of(coveredOrganos));
  }

  private static String readOf(ImportRunId runId) {
    return "/api/admin/import-run/" + runId;
  }
}
