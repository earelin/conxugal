package gal.conxugal.application.rest.admin.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class ImportOrganosControllerIntegrationTest extends AuthenticationTestSupport {

  @Inject
  ImportOrganos importOrganos;

  @MockBean(ImportOrganos.class)
  ImportOrganos importOrganosMock() {
    return mock(ImportOrganos.class);
  }

  @Test
  void admin_triggers_import_and_gets_outcome(RequestSpecification spec) {
    Response response = triggerImportAsAdmin(spec, ImportOutcome.success(3, 12, 1));

    assertThat(response.jsonPath().getString("status")).isEqualTo("SUCCESS");
    assertThat(response.jsonPath().getInt("added")).isEqualTo(3);
    assertThat(response.jsonPath().getInt("refreshed")).isEqualTo(12);
    assertThat(response.jsonPath().getInt("deactivated")).isEqualTo(1);
  }

  @Test
  void admin_gets_already_running_outcome(RequestSpecification spec) {
    Response response = triggerImportAsAdmin(spec, ImportOutcome.alreadyRunning());

    assertThat(response.jsonPath().getString("status")).isEqualTo("ALREADY_RUNNING");
    assertThat(response.jsonPath().getInt("added")).isZero();
    assertThat(response.jsonPath().getInt("refreshed")).isZero();
    assertThat(response.jsonPath().getInt("deactivated")).isZero();
  }

  @Test
  void source_failure_is_reported_as_failure_outcome(RequestSpecification spec) {
    Response response = triggerImportAsAdmin(spec, ImportOutcome.failure());

    assertThat(response.jsonPath().getString("status")).isEqualTo("FAILURE");
    assertThat(response.jsonPath().getInt("added")).isZero();
    assertThat(response.jsonPath().getInt("refreshed")).isZero();
    assertThat(response.jsonPath().getInt("deactivated")).isZero();
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .post("/api/admin/organos/import")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
    verifyNoInteractions(importOrganos);
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .post("/api/admin/organos/import")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
    verifyNoInteractions(importOrganos);
  }

  private Response triggerImportAsAdmin(RequestSpecification spec, ImportOutcome outcome) {
    when(importOrganos.run()).thenReturn(outcome);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .post("/api/admin/organos/import");

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }
}
