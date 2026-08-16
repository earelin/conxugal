package gal.conxugal.application.rest.organos;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.contrato.ContratosMenoresSection;
import gal.conxugal.domain.contrato.DescribeContratosMenoresSection;
import gal.conxugal.domain.contrato.VisibleContratoMenorRepository;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.ViewOrgano;
import gal.conxugal.domain.user.User;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Which Órganos hold a section, and which years one offers, are settled by
// DescribeContratosMenoresSectionTest and the JDBC suites behind it. Both collaborators are
// mocked here, so what this suite asserts is the shape of the envelope: that a section becomes a
// family entry, that no section becomes an empty map rather than a missing key, and that an
// unknown id is refused with the one problem type the whole API uses for it.
@MicronautTest
class OrganoControllerIntegrationTest extends AuthenticationTestSupport {

  private static final OrganoId SERGAS = new OrganoId(UUID.randomUUID());

  @Inject
  ViewOrgano viewOrgano;

  @Inject
  DescribeContratosMenoresSection contratosMenoresSection;

  @MockBean(ViewOrgano.class)
  ViewOrgano viewOrganoMock() {
    return mock(ViewOrgano.class);
  }

  @MockBean(DescribeContratosMenoresSection.class)
  DescribeContratosMenoresSection contratosMenoresSectionMock() {
    return mock(DescribeContratosMenoresSection.class);
  }

  // Mocking a concrete use case still has Micronaut resolve the real constructor's arguments, and
  // this one asks for the visible-contract port — which in this suite means the JDBC adapter, in a
  // context with no datasource. The port is stubbed too, so nothing here reaches for one.
  //
  // Replacement is by assignability, so this removes the adapter bean outright. Nothing in this
  // suite wants it; a test added here that reaches a contract endpoint will need its own mock
  // rather than a datasource.
  @MockBean(VisibleContratoMenorRepository.class)
  VisibleContratoMenorRepository visibleContratoMenorRepositoryMock() {
    return mock(VisibleContratoMenorRepository.class);
  }

  @Test
  void user_reads_an_organo_with_the_years_and_flags_of_the_family_it_holds(
      RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(
        Optional.of(
            new ContratosMenoresSection(
                List.of(YearSelection.of(2025), YearSelection.of(2024), YearSelection.of(2023)),
                false, true)));

    Response response = readAs(spec, TestUserFactory.normalUser(), SERGAS);

    assertThat(response.jsonPath().getString("id")).isEqualTo(SERGAS.toString());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Servizo Galego de Saúde");
    assertThat(response.jsonPath().getList("families.'contratos-menores'.years", Integer.class))
        .containsExactly(2025, 2024, 2023);
    assertThat(response.jsonPath().getBoolean("families.'contratos-menores'.partial")).isFalse();
    assertThat(response.jsonPath().getBoolean("families.'contratos-menores'.updating")).isTrue();
  }

  // The page renders "this Órgano holds no contracts" from the emptiness itself, so an absent key
  // would be a different fact. getMap on the object is what tells "sent as {}" from "not sent" —
  // a jsonPath lookup of a missing key answers null either way.
  @Test
  void an_organo_holding_no_visible_contract_answers_an_empty_families_object(
      RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(Optional.empty());

    Response response = readAs(spec, TestUserFactory.normalUser(), SERGAS);

    assertThat(response.jsonPath().getMap("$")).containsEntry("families", Map.of());
  }

  // The opposite half of the same requirement, and the one a null-tolerant serializer would get
  // wrong quietly: a contratos-menores key sent as null would have the client count a family this
  // Órgano does not hold, and draw a tab with nothing behind it.
  @Test
  void family_with_no_data_is_absent_rather_than_null(RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(Optional.empty());

    Response response = readAs(spec, TestUserFactory.normalUser(), SERGAS);

    assertThat(response.jsonPath().getMap("families")).doesNotContainKey("contratos-menores");
  }

  // Visibility is a property of the contracts, not of the reader, so an Órgano a USER would never
  // meet through GET /api/organos is neither hidden nor refused here — it simply holds no family.
  // A 403 or a 404 in this case would make an Órgano's identity a secret, which it is not.
  @Test
  void organo_outside_the_visible_set_is_answered_rather_than_refused(RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(Optional.empty());
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(memberOf(SERGAS));

    assertThat(response.getStatusCode())
        .as("neither 403 nor 404: an Órgano nothing is visible for is answered, not refused")
        .isEqualTo(HttpStatus.OK.getCode());
  }

  @Test
  void admin_and_user_read_the_same_member(RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(
        Optional.of(new ContratosMenoresSection(List.of(YearSelection.of(2025)), true, false)));

    Response asUser = readAs(spec, TestUserFactory.normalUser(), SERGAS);
    Response asAdmin = readAs(spec, TestUserFactory.adminUser(), SERGAS);

    assertThat(asAdmin.asString()).isEqualTo(asUser.asString());
  }

  // This operation carries summaries and never a contract, and it says nothing about the
  // catalogue row's state or placement either. Asserted on the key set rather than on the record's
  // shape, because every other assertion here names the keys it wants and so cannot see an extra.
  @Test
  void the_member_carries_no_contract_and_none_of_the_catalogue_rows_fields(
      RequestSpecification spec) {
    stubOrgano();
    when(contratosMenoresSection.describe(SERGAS)).thenReturn(
        Optional.of(new ContratosMenoresSection(List.of(YearSelection.of(2025)), false, false)));

    Response response = readAs(spec, TestUserFactory.normalUser(), SERGAS);

    assertThat(response.jsonPath().getMap("$")).containsOnlyKeys("id", "name", "families");
  }

  // The same type every other operation naming an unknown Órgano answers with. A second type for
  // one condition would have every client learn both, and this is what would fail if one appeared.
  @Test
  void unknown_organo_is_organo_not_found(RequestSpecification spec) {
    OrganoId unknown = new OrganoId(UUID.randomUUID());
    when(viewOrgano.view(unknown)).thenThrow(new OrganoNotFoundException(unknown));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(memberOf(unknown));

    assertProblem(response)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasType("urn:conxugal:problem-type:organo-not-found");
  }

  // The status and the body together: 401 alone would still pass if the refusal carried the
  // Órgano beside it, and a name is what an unauthenticated caller must not learn from a path
  // that names one. Refused by the filter chain, so neither collaborator is stubbed here.
  @Test
  void unauthenticated_caller_is_unauthorized_and_reads_no_organo(RequestSpecification spec) {
    Response response =
        given(spec)
        .when()
            .get(memberOf(SERGAS));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    assertThat(response.jsonPath().getMap("$"))
        .doesNotContainKeys("id", "name", "families");
  }

  private void stubOrgano() {
    when(viewOrgano.view(SERGAS)).thenReturn(
        new OrganoDeContratacion(
            SERGAS, "test-sergas", "Servizo Galego de Saúde", true, true, null));
  }

  private Response readAs(RequestSpecification spec, User user, OrganoId organoId) {
    String sessionCookie = seedUserAndLoginAs(spec, user);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get(memberOf(organoId));

    response.then().statusCode(HttpStatus.OK.getCode());
    return response;
  }

  private static String memberOf(OrganoId organoId) {
    return "/api/organo/" + organoId;
  }
}
