package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.ListVisibleOrganos;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganosWithVisibleContracts;
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
// database — this suite mocks its domain collaborators and never reaches one. Which Órganos
// make up the visible set is settled the same way: ListVisibleOrganosTest and
// JdbcContratoMenorVisibleOrganosIntegrationTest own the predicate, and what is asserted here is
// that this path serves that use case rather than the whole catalogue.
@MicronautTest
class OrganosControllerIntegrationTest extends AuthenticationTestSupport {

  private static final TermoId SANIDADE = new TermoId(UUID.randomUUID());
  private static final TermoId HOSPITAIS = new TermoId(UUID.randomUUID());

  @Inject
  ListVisibleOrganos listVisibleOrganos;

  @Inject
  ListTermos listTermos;

  @MockBean(ListVisibleOrganos.class)
  ListVisibleOrganos listVisibleOrganosMock() {
    return mock(ListVisibleOrganos.class);
  }

  // Mocking a concrete use case still has Micronaut resolve the real constructor's arguments, and
  // this one asks for every contract family — which in this suite means the JDBC adapter, in a
  // context with no datasource. The port is stubbed too, so nothing here reaches for one.
  @MockBean(OrganosWithVisibleContracts.class)
  OrganosWithVisibleContracts organosWithVisibleContractsMock() {
    return mock(OrganosWithVisibleContracts.class);
  }

  @MockBean(ListTermos.class)
  ListTermos listTermosMock() {
    return mock(ListTermos.class);
  }

  @Test
  void user_reads_the_visible_set_with_each_organos_name_state_and_placement(
      RequestSpecification spec) {
    OrganoId marId = new OrganoId(UUID.randomUUID());
    OrganoId sanidadeId = new OrganoId(UUID.randomUUID());
    when(listVisibleOrganos.list()).thenReturn(
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

  // Asserted on the absence of a key rather than left to the response record's shape, because
  // every other assertion here names the keys it wants and so cannot see an extra one. Which
  // Órganos are marked is an administration capability: folding importable into this read —
  // the obvious "why two records?" refactor — would hand it to a USER, and nothing else in the
  // suite would fail.
  @Test
  void catalogue_read_withholds_the_import_mark_from_users(RequestSpecification spec) {
    when(listVisibleOrganos.list()).thenReturn(
        List.of(new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "mar",
            "Consellería do Mar", true, true, null)));

    Response response = readAs(spec, TestUserFactory.normalUser(), "/api/organos");

    assertThat(response.jsonPath().getMap("[0]")).doesNotContainKey("importable");
  }

  // Nothing imported yet, or nothing complete enough to show, is an empty body rather than a
  // fallback to the catalogue — the narrowing has no degenerate case that widens back out.
  @Test
  void serves_an_empty_body_when_no_organo_holds_any_visible_contract(RequestSpecification spec) {
    when(listVisibleOrganos.list()).thenReturn(List.of());

    Response response = readAs(spec, TestUserFactory.normalUser(), "/api/organos");

    assertThat(response.jsonPath().getList("$")).isEmpty();
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
    when(listVisibleOrganos.list()).thenReturn(
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

  // The narrowing is this path's, not the caller's: an ADMIN gets no more here than a USER does,
  // and reaches the whole catalogue through GET /api/admin/organos instead. A role check on this
  // endpoint would give one path two meanings, and this is what would fail if one were added.
  @Test
  void admin_and_user_read_the_same_visible_set(RequestSpecification spec) {
    when(listVisibleOrganos.list()).thenReturn(
        List.of(new OrganoDeContratacion(new OrganoId(UUID.randomUUID()), "sanidade",
            "Consellería de Sanidade", true, false, SANIDADE)));

    Response asUser = readAs(spec, TestUserFactory.normalUser(), "/api/organos");
    Response asAdmin = readAs(spec, TestUserFactory.adminUser(), "/api/organos");

    assertThat(asAdmin.asString()).isEqualTo(asUser.asString());
  }

  @Test
  void empty_taxonomia_leaves_the_whole_visible_set_unclassified(RequestSpecification spec) {
    when(listVisibleOrganos.list()).thenReturn(
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
