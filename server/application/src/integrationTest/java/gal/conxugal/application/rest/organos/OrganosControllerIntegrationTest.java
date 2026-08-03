package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.ListOrganos;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.ListTermos;
import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import gal.conxugal.domain.user.User;
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

// Every stub below is deliberately handed to the controller out of name order, so what is
// asserted is that it serves the list verbatim. The order itself is the repository's, under
// the Galician collation Jdbc{Organo,Termo}RepositoryIntegrationTest asserts against a real
// database — this suite mocks its domain collaborators and never reaches one.
@MicronautTest
class OrganosControllerIntegrationTest extends AuthenticationTestSupport {

  private static final TermoId SANIDADE = new TermoId(UUID.randomUUID());
  private static final TermoId HOSPITAIS = new TermoId(UUID.randomUUID());

  @Inject
  ListOrganos listOrganos;

  @Inject
  ListTermos listTermos;

  @MockBean(ListOrganos.class)
  ListOrganos listOrganosMock() {
    return mock(ListOrganos.class);
  }

  @MockBean(ListTermos.class)
  ListTermos listTermosMock() {
    return mock(ListTermos.class);
  }

  @Test
  void user_reads_every_organo_with_its_name_state_and_placement(RequestSpecification spec) {
    OrganoId marId = new OrganoId(UUID.randomUUID());
    OrganoId sanidadeId = new OrganoId(UUID.randomUUID());
    when(listOrganos.list()).thenReturn(
        List.of(
            new OrganoDeContratacion(marId, "mar", "Consellería do Mar", false, false, null),
            new OrganoDeContratacion(sanidadeId, "sanidade", "Consellería de Sanidade", true,
                false, SANIDADE)));

    Response response = readAs(spec, TestUserFactory.normalUser(), "/api/organos");

    assertThat(response.jsonPath().getList("id", String.class))
        .containsExactly(marId.toString(), sanidadeId.toString());
    assertThat(response.jsonPath().getList("name", String.class))
        .containsExactly("Consellería do Mar", "Consellería de Sanidade");
    assertThat(response.jsonPath().getList("active", Boolean.class))
        .containsExactly(false, true);
    assertThat(response.jsonPath().getList("termoId", String.class))
        .containsExactly(null, SANIDADE.toString());
    // getList spreads over the array and yields null for an absent key too, so the
    // unclassified case needs the object itself to tell "sent as null" from "not sent".
    assertThat(response.jsonPath().getMap("[0]")).containsEntry("termoId", null);
  }

  @Test
  void user_reads_every_term_with_its_parent_edge(RequestSpecification spec) {
    when(listTermos.list()).thenReturn(
        List.of(
            new Termo(SANIDADE, "Sanidade", null),
            new Termo(HOSPITAIS, "Hospitais", SANIDADE)));

    Response response = readAs(spec, TestUserFactory.normalUser(), "/api/organos/taxonomia");

    assertThat(response.jsonPath().getList("id", String.class))
        .containsExactly(SANIDADE.toString(), HOSPITAIS.toString());
    assertThat(response.jsonPath().getList("name", String.class))
        .containsExactly("Sanidade", "Hospitais");
    assertThat(response.jsonPath().getList("parentId", String.class))
        .containsExactly(null, SANIDADE.toString());
    assertThat(response.jsonPath().getMap("[0]")).containsEntry("parentId", null);
  }

  @Test
  void admin_reads_both_lists_too(RequestSpecification spec) {
    when(listOrganos.list()).thenReturn(
        List.of(new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "sanidade",
            "Consellería de Sanidade", true, false, SANIDADE)));
    when(listTermos.list()).thenReturn(List.of(new Termo(SANIDADE, "Sanidade", null)));
    User admin = TestUserFactory.adminUser();

    assertThat(readAs(spec, admin, "/api/organos").jsonPath().getList("name", String.class))
        .containsExactly("Consellería de Sanidade");
    assertThat(
        readAs(spec, admin, "/api/organos/taxonomia").jsonPath().getList("name", String.class))
        .containsExactly("Sanidade");
  }

  @Test
  void empty_taxonomia_leaves_the_whole_catalogue_unclassified(RequestSpecification spec) {
    when(listOrganos.list()).thenReturn(
        List.of(
            new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "mar", "Consellería do Mar",
                true, false, null),
            new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "sanidade",
                "Consellería de Sanidade", true, false, null)));
    when(listTermos.list()).thenReturn(List.of());
    User user = TestUserFactory.normalUser();

    Response taxonomia = readAs(spec, user, "/api/organos/taxonomia");
    Response catalogue = readAs(spec, user, "/api/organos");

    assertThat(taxonomia.jsonPath().getList("id", String.class)).isEmpty();
    assertThat(catalogue.jsonPath().getList("name", String.class))
        .containsExactly("Consellería do Mar", "Consellería de Sanidade");
    assertThat(catalogue.jsonPath().getList("termoId", String.class))
        .containsExactly(null, null);
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_the_catalogue(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/organos")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_the_taxonomia(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/organos/taxonomia")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private Response readAs(RequestSpecification spec, User user, String path) {
    String sessionCookie = seedUserAndLoginAs(spec, user);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(path);

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }
}
