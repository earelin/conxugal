package gal.conxugal.application.rest.admin.organos;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.application.rest.admin.support.ContratosMenoresImportTestSupport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.MarkOrganoForImport;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotEligibleForImportException;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.UnmarkOrganoForImport;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Both writes hang off the Órgano's own path rather than /api/admin/organos, so the ADMIN gate
// a rule shaped around the plural would miss is asserted here per operation.
@MicronautTest
class OrganoImportMarkControllerIntegrationTest extends ContratosMenoresImportTestSupport {

  private static final OrganoId SANIDADE = new OrganoId(UUID.randomUUID());
  private static final ImportRunId RUN = new ImportRunId(UUID.randomUUID());

  @Inject
  MarkOrganoForImport markOrganoForImport;

  @Inject
  UnmarkOrganoForImport unmarkOrganoForImport;

  @MockBean(MarkOrganoForImport.class)
  MarkOrganoForImport markOrganoForImportMock() {
    return mock(MarkOrganoForImport.class);
  }

  @MockBean(UnmarkOrganoForImport.class)
  UnmarkOrganoForImport unmarkOrganoForImportMock() {
    return mock(UnmarkOrganoForImport.class);
  }

  // Called twice on purpose: marking is idempotent, so an admin flicking the switch back on
  // must not see the second call fail.
  @Test
  void admin_marks_organo_and_may_mark_it_again(RequestSpecification spec) {
    when(startImport.startOrgano(SANIDADE)).thenReturn(RUN);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.OK.getCode());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.OK.getCode());
  }

  // #5, immediate half: marking with no import running starts one for that Órgano alone and
  // answers with its run identifier.
  @Test
  void mark_with_no_import_running_starts_one_and_answers_with_its_run(
      RequestSpecification spec) {
    when(startImport.startOrgano(SANIDADE)).thenReturn(RUN);

    Response response = markAsAdmin(spec);

    assertThat(response.jsonPath().getString("runId")).isEqualTo(RUN.value().toString());
    assertThat(response.jsonPath().getString("refusal")).isNull();
  }

  // #33, refused-and-kept half: a mark landing while an import runs is refused rather than
  // queued, and the mark itself stands — which is why this is a 200 and not a 409.
  @Test
  void mark_while_an_import_runs_keeps_the_mark_and_reports_the_refusal(
      RequestSpecification spec) {
    when(startImport.startOrgano(SANIDADE)).thenThrow(new ImportAlreadyRunningException());

    Response response = markAsAdmin(spec);

    assertThat(response.jsonPath().getString("runId")).isNull();
    assertThat(response.jsonPath().getString("refusal")).isEqualTo("IMPORT_ALREADY_RUNNING");
    // The one thing an assertion on the response cannot show: that the mark was written anyway.
    verify(markOrganoForImport).mark(SANIDADE);
  }

  @Test
  void mark_of_an_ineligible_organo_keeps_the_mark_and_reports_the_refusal(
      RequestSpecification spec) {
    when(startImport.startOrgano(SANIDADE))
        .thenThrow(new OrganoNotEligibleForImportException(SANIDADE));

    Response response = markAsAdmin(spec);

    assertThat(response.jsonPath().getString("runId")).isNull();
    assertThat(response.jsonPath().getString("refusal")).isEqualTo("ORGANO_NOT_ELIGIBLE");
    verify(markOrganoForImport).mark(SANIDADE);
  }

  @Test
  void admin_unmarks_organo_and_may_unmark_it_again(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  @Test
  void mark_of_unknown_organo_is_organo_not_found(RequestSpecification spec) {
    doThrow(new OrganoNotFoundException(SANIDADE)).when(markOrganoForImport).mark(SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .put(markOf(SANIDADE));

    assertProblem(response)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:organo-not-found");
  }

  @Test
  void unmark_of_unknown_organo_is_organo_not_found(RequestSpecification spec) {
    doThrow(new OrganoNotFoundException(SANIDADE)).when(unmarkOrganoForImport).unmark(SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .delete(markOf(SANIDADE));

    assertProblem(response)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:organo-not-found");
  }

  @Test
  void user_role_is_forbidden_on_mark(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void user_role_is_forbidden_on_unmark(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_mark(RequestSpecification spec) {
    given(spec)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_unmark(RequestSpecification spec) {
    given(spec)
    .when()
        .delete(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private Response markAsAdmin(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .put(markOf(SANIDADE));

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }

  private static String markOf(OrganoId organoId) {
    return "/api/admin/organo/" + organoId + "/importable";
  }
}
