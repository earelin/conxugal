package gal.conxugal.application.rest.admin.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.ListOrganos;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The list is served verbatim in the order the repository delivers, which is why every stub
// here is handed over in an order the controller must not re-sort. The Galician collation that
// produces that order is JdbcOrganoRepositoryIntegrationTest's to assert against a real
// database; this suite mocks its domain collaborator and never reaches one.
@MicronautTest
class AdminOrganosControllerIntegrationTest extends AuthenticationTestSupport {

  private static final TermoId CONSELLERIAS = new TermoId(UUID.randomUUID());

  @Inject
  ListOrganos listOrganos;

  @MockBean(ListOrganos.class)
  ListOrganos listOrganosMock() {
    return mock(ListOrganos.class);
  }

  @Test
  void admin_reads_every_organo_with_its_placement_and_its_mark(RequestSpecification spec) {
    OrganoId marId = new OrganoId(UUID.randomUUID());
    OrganoId sanidadeId = new OrganoId(UUID.randomUUID());
    OrganoId facendaId = new OrganoId(UUID.randomUUID());
    when(listOrganos.list()).thenReturn(
        List.of(
            new OrganoDeContratacion(marId, "mar", "Consellería do Mar", false, true, null),
            new OrganoDeContratacion(sanidadeId, "sanidade", "Consellería de Sanidade", true,
                true, CONSELLERIAS),
            new OrganoDeContratacion(facendaId, "facenda", "Consellería de Facenda", true, false,
                CONSELLERIAS)));

    Response response = readCatalogueAsAdmin(spec);

    assertThat(response.jsonPath().getList("id", String.class))
        .containsExactly(marId.toString(), sanidadeId.toString(), facendaId.toString());
    assertThat(response.jsonPath().getList("name", String.class))
        .containsExactly("Consellería do Mar", "Consellería de Sanidade",
            "Consellería de Facenda");
    assertThat(response.jsonPath().getList("active", Boolean.class))
        .containsExactly(false, true, true);
    assertThat(response.jsonPath().getList("termoId", String.class))
        .containsExactly(null, CONSELLERIAS.toString(), CONSELLERIAS.toString());
    assertThat(response.jsonPath().getList("importable", Boolean.class))
        .containsExactly(true, true, false);
  }

  // getList spreads over the array and yields null for an absent key too, so the unclassified
  // case needs the object itself to tell "sent as null" from "not sent".
  @Test
  void serves_an_unclassified_organo_with_an_explicit_null_placement(RequestSpecification spec) {
    when(listOrganos.list()).thenReturn(
        List.of(new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "mar",
            "Consellería do Mar", true, false, null)));

    Response response = readCatalogueAsAdmin(spec);

    assertThat(response.jsonPath().getMap("[0]")).containsEntry("termoId", null);
  }

  @Test
  void serves_an_empty_catalogue_before_the_first_import(RequestSpecification spec) {
    when(listOrganos.list()).thenReturn(List.of());

    Response response = readCatalogueAsAdmin(spec);

    assertThat(response.jsonPath().getList("id", String.class)).isEmpty();
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/organos")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/admin/organos")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private Response readCatalogueAsAdmin(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/admin/organos");

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }
}
