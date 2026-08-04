package gal.conxugal.application.rest.admin.organos;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.MarkOrganoForImport;
import gal.conxugal.domain.organo.OrganoId;
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
class OrganoImportMarkControllerIntegrationTest extends AuthenticationTestSupport {

  private static final OrganoId SANIDADE = new OrganoId(UUID.randomUUID());

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
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .put(markOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
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

  private static String markOf(OrganoId organoId) {
    return "/api/admin/organo/" + organoId + "/importable";
  }
}
