package gal.conxugal.application.rest.admin.organos;

import static gal.conxugal.application.http.error.support.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.AssignOrganoToTermo;
import gal.conxugal.domain.organo.ClearOrganoTermo;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import gal.conxugal.domain.organo.taxonomia.TermoNotFoundException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// These two hang off the Órgano's own path rather than a taxonomía one, so the ADMIN gate a
// rule shaped around /api/admin/organos/taxonomia/** would miss is asserted here per
// operation rather than once for the feature.
@MicronautTest
class OrganoClassificationControllerIntegrationTest extends AuthenticationTestSupport {

  private static final OrganoId SANIDADE = new OrganoId(UUID.randomUUID());
  private static final TermoId CONSELLERIAS = new TermoId(UUID.randomUUID());

  @Inject
  AssignOrganoToTermo assignOrganoToTermo;

  @Inject
  ClearOrganoTermo clearOrganoTermo;

  @MockBean(AssignOrganoToTermo.class)
  AssignOrganoToTermo assignOrganoToTermoMock() {
    return mock(AssignOrganoToTermo.class);
  }

  @MockBean(ClearOrganoTermo.class)
  ClearOrganoTermo clearOrganoTermoMock() {
    return mock(ClearOrganoTermo.class);
  }

  @Test
  void admin_files_organo_under_term(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"termoId":"%s"}\
            """.formatted(CONSELLERIAS))
    .when()
        .put(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  @Test
  void assign_to_unknown_organo_is_organo_not_found(RequestSpecification spec) {
    doThrow(new OrganoNotFoundException(SANIDADE))
        .when(assignOrganoToTermo).assign(SANIDADE, CONSELLERIAS);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"termoId":"%s"}\
                """.formatted(CONSELLERIAS))
        .when()
            .put(placementOf(SANIDADE));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:organo-not-found");
  }

  // An unknown Órgano and an unknown term share a status, so the type is the only thing
  // telling a client which of the two ids it got wrong.
  @Test
  void assign_to_unknown_term_is_termo_not_found(RequestSpecification spec) {
    doThrow(new TermoNotFoundException(CONSELLERIAS))
        .when(assignOrganoToTermo).assign(SANIDADE, CONSELLERIAS);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"termoId":"%s"}\
                """.formatted(CONSELLERIAS))
        .when()
            .put(placementOf(SANIDADE));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void assign_without_term_is_bad_request(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{}")
    .when()
        .put(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.BAD_REQUEST.getCode());
  }

  // Called twice on purpose: clearing is idempotent, so an admin double-clicking a row must
  // not see the second call fail.
  @Test
  void admin_clears_placement_and_may_clear_it_again(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  @Test
  void clear_of_unknown_organo_is_organo_not_found(RequestSpecification spec) {
    doThrow(new OrganoNotFoundException(SANIDADE)).when(clearOrganoTermo).clear(SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .delete(placementOf(SANIDADE));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:organo-not-found");
  }

  @Test
  void user_role_is_forbidden_on_assign(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"termoId":"%s"}\
            """.formatted(CONSELLERIAS))
    .when()
        .put(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void user_role_is_forbidden_on_clear(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_assign(RequestSpecification spec) {
    given(spec)
        .body(
            """
            {"termoId":"%s"}\
            """.formatted(CONSELLERIAS))
    .when()
        .put(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_clear(RequestSpecification spec) {
    given(spec)
    .when()
        .delete(placementOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private static String placementOf(OrganoId organoId) {
    return "/api/admin/organo/" + organoId + "/termo";
  }
}
