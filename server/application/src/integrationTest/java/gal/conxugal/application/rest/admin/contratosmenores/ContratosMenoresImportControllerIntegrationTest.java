package gal.conxugal.application.rest.admin.contratosmenores;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.application.rest.admin.support.ContratosMenoresImportTestSupport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The two triggers, which answer with a run rather than with an outcome: an initial import runs
// for days, so the response cannot wait for it.
@MicronautTest
class ContratosMenoresImportControllerIntegrationTest extends ContratosMenoresImportTestSupport {

  private static final ImportRunId RUN = new ImportRunId(UUID.randomUUID());
  private static final OrganoId SERGAS = new OrganoId(UUID.randomUUID());
  private static final String SWEEP = "/api/admin/contratos-menores/import";

  @Test
  void admin_triggers_sweep_and_gets_the_run_it_started(RequestSpecification spec) {
    when(startImport.startAll()).thenReturn(RUN);

    Response response = postAsAdmin(spec, SWEEP);

    response.then().statusCode(HttpStatus.ACCEPTED.getCode());
    assertThat(response.jsonPath().getString("runId")).isEqualTo(RUN.value().toString());
    assertThat(response.header(HttpHeaders.LOCATION))
        .isEqualTo("/api/admin/import-run/" + RUN.value());
  }

  @Test
  void admin_triggers_one_organo_and_gets_the_run_it_started(RequestSpecification spec) {
    when(startImport.startOrgano(SERGAS)).thenReturn(RUN);

    Response response = postAsAdmin(spec, organoTriggerOf(SERGAS));

    response.then().statusCode(HttpStatus.ACCEPTED.getCode());
    assertThat(response.jsonPath().getString("runId")).isEqualTo(RUN.value().toString());
    assertThat(response.header(HttpHeaders.LOCATION))
        .isEqualTo("/api/admin/import-run/" + RUN.value());
  }

  // #32: refused rather than queued, and nothing is started.
  @Test
  void sweep_while_an_import_runs_is_refused_as_already_running(RequestSpecification spec) {
    when(startImport.startAll()).thenThrow(new ImportAlreadyRunningException());

    assertProblem(postAsAdmin(spec, SWEEP))
        .hasStatus(HttpStatus.CONFLICT)
        .hasType("urn:conxugal:problem-type:import-already-running");
  }

  @Test
  void organo_trigger_while_an_import_runs_is_refused_as_already_running(
      RequestSpecification spec) {
    when(startImport.startOrgano(SERGAS)).thenThrow(new ImportAlreadyRunningException());

    assertProblem(postAsAdmin(spec, organoTriggerOf(SERGAS)))
        .hasStatus(HttpStatus.CONFLICT)
        .hasType("urn:conxugal:problem-type:import-already-running");
  }

  // #34: a different problem type from the guard refusal, so a client that can only read the
  // status is not left unable to tell waiting from marking the Órgano.
  @Test
  void organo_trigger_naming_an_ineligible_organo_is_refused_as_not_eligible(
      RequestSpecification spec) {
    when(startImport.startOrgano(SERGAS))
        .thenThrow(new OrganoNotEligibleForImportException(SERGAS));

    assertProblem(postAsAdmin(spec, organoTriggerOf(SERGAS)))
        .hasStatus(HttpStatus.CONFLICT)
        .hasType("urn:conxugal:problem-type:organo-not-eligible");
  }

  @Test
  void organo_trigger_naming_an_unknown_organo_is_organo_not_found(RequestSpecification spec) {
    when(startImport.startOrgano(SERGAS)).thenThrow(new OrganoNotFoundException(SERGAS));

    assertProblem(postAsAdmin(spec, organoTriggerOf(SERGAS)))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:organo-not-found");
  }

  @Test
  void user_role_is_forbidden_on_sweep(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .post(SWEEP)
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
    verifyNoInteractions(startImport);
  }

  @Test
  void user_role_is_forbidden_on_organo_trigger(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .post(organoTriggerOf(SERGAS))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
    verifyNoInteractions(startImport);
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_sweep(RequestSpecification spec) {
    given(spec)
    .when()
        .post(SWEEP)
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
    verifyNoInteractions(startImport);
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_organo_trigger(RequestSpecification spec) {
    given(spec)
    .when()
        .post(organoTriggerOf(SERGAS))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
    verifyNoInteractions(startImport);
  }

  private Response postAsAdmin(RequestSpecification spec, String path) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .post(path);
  }

  private static String organoTriggerOf(OrganoId organoId) {
    return "/api/admin/organo/" + organoId + "/contratos-menores/import";
  }
}
