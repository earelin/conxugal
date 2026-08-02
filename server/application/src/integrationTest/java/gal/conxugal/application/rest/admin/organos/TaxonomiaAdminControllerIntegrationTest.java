package gal.conxugal.application.rest.admin.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.taxonomia.CreateTermo;
import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.DuplicateSiblingNameException;
import gal.conxugal.domain.organo.taxonomia.MoveTermo;
import gal.conxugal.domain.organo.taxonomia.RenameTermo;
import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoCycleException;
import gal.conxugal.domain.organo.taxonomia.TermoHasChildrenException;
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

// The rules themselves — the cycle guard, the child-term refusal, the sibling-name check —
// belong to the use cases, and are proven against a test double in domain and against a real
// database in infrastructure. What this suite proves is the wire contract each refusal
// reaches a client through: its status, and the problem type that tells two 409s apart.
@MicronautTest
class TaxonomiaAdminControllerIntegrationTest extends AuthenticationTestSupport {

  private static final String TERMOS = "/api/admin/organos/taxonomia/termos";
  private static final TermoId SANIDADE = new TermoId(UUID.randomUUID());
  private static final TermoId HOSPITAIS = new TermoId(UUID.randomUUID());

  @Inject
  CreateTermo createTermo;

  @Inject
  RenameTermo renameTermo;

  @Inject
  MoveTermo moveTermo;

  @Inject
  DeleteTermo deleteTermo;

  @MockBean(CreateTermo.class)
  CreateTermo createTermoMock() {
    return mock(CreateTermo.class);
  }

  @MockBean(RenameTermo.class)
  RenameTermo renameTermoMock() {
    return mock(RenameTermo.class);
  }

  @MockBean(MoveTermo.class)
  MoveTermo moveTermoMock() {
    return mock(MoveTermo.class);
  }

  @MockBean(DeleteTermo.class)
  DeleteTermo deleteTermoMock() {
    return mock(DeleteTermo.class);
  }

  @Test
  void admin_creates_root_term_and_gets_its_new_id(RequestSpecification spec) {
    when(createTermo.create("Sanidade", null)).thenReturn(new Termo(SANIDADE, "Sanidade", null));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Sanidade","parentId":null}\
                """)
        .when()
            .post(TERMOS);

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("id")).isEqualTo(SANIDADE.toString());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Sanidade");
    assertThat(response.jsonPath().getMap("$")).containsEntry("parentId", null);
  }

  @Test
  void admin_creates_term_under_parent(RequestSpecification spec) {
    when(createTermo.create("Hospitais", SANIDADE))
        .thenReturn(new Termo(HOSPITAIS, "Hospitais", SANIDADE));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Hospitais","parentId":"%s"}\
                """.formatted(SANIDADE))
        .when()
            .post(TERMOS);

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("id")).isEqualTo(HOSPITAIS.toString());
    assertThat(response.jsonPath().getString("parentId")).isEqualTo(SANIDADE.toString());
  }

  @Test
  void create_under_unknown_parent_is_termo_not_found(RequestSpecification spec) {
    when(createTermo.create("Hospitais", SANIDADE))
        .thenThrow(new TermoNotFoundException(SANIDADE));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Hospitais","parentId":"%s"}\
                """.formatted(SANIDADE))
        .when()
            .post(TERMOS);

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void create_colliding_with_sibling_name_is_duplicate_sibling_name(RequestSpecification spec) {
    when(createTermo.create("Sanidade", null))
        .thenThrow(new DuplicateSiblingNameException("Sanidade", null));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Sanidade","parentId":null}\
                """)
        .when()
            .post(TERMOS);

    assertProblem(
        response, HttpStatus.CONFLICT, "urn:conxugal:problem-type:duplicate-sibling-name");
  }

  @Test
  void create_with_blank_name_is_bad_request(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"name":"   ","parentId":null}\
            """)
    .when()
        .post(TERMOS)
    .then()
        .statusCode(HttpStatus.BAD_REQUEST.getCode());
  }

  @Test
  void create_with_name_longer_than_the_column_is_bad_request(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"name":"%s","parentId":null}\
            """.formatted("a".repeat(256)))
    .when()
        .post(TERMOS)
    .then()
        .statusCode(HttpStatus.BAD_REQUEST.getCode());
  }

  // A multi-word name padded with whitespace, because the two are easy to conflate and only
  // one of them is the use case's to remove: the spaces *between* words are part of the name
  // — most terms are several words — while the surrounding ones are stripped. The edge must
  // reject neither, which is what the request schema's pattern is written to allow.
  @Test
  void create_with_padded_multi_word_name_keeps_the_spaces_between_words(
      RequestSpecification spec) {
    when(createTermo.create("  Sanidade e Benestar  ", null))
        .thenReturn(new Termo(SANIDADE, "Sanidade e Benestar", null));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"  Sanidade e Benestar  ","parentId":null}\
                """)
        .when()
            .post(TERMOS);

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Sanidade e Benestar");
  }

  @Test
  void admin_renames_term_and_gets_its_new_state(RequestSpecification spec) {
    when(renameTermo.rename(SANIDADE, "Sanidade e Benestar"))
        .thenReturn(new Termo(SANIDADE, "Sanidade e Benestar", null));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Sanidade e Benestar"}\
                """)
        .when()
            .patch(termo(SANIDADE));

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getString("id")).isEqualTo(SANIDADE.toString());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Sanidade e Benestar");
  }

  @Test
  void rename_of_unknown_term_is_termo_not_found(RequestSpecification spec) {
    when(renameTermo.rename(SANIDADE, "Sanidade e Benestar"))
        .thenThrow(new TermoNotFoundException(SANIDADE));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Sanidade e Benestar"}\
                """)
        .when()
            .patch(termo(SANIDADE));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void rename_colliding_with_sibling_name_is_duplicate_sibling_name(RequestSpecification spec) {
    when(renameTermo.rename(HOSPITAIS, "Centros"))
        .thenThrow(new DuplicateSiblingNameException("Centros", SANIDADE));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"name":"Centros"}\
                """)
        .when()
            .patch(termo(HOSPITAIS));

    assertProblem(
        response, HttpStatus.CONFLICT, "urn:conxugal:problem-type:duplicate-sibling-name");
  }

  @Test
  void admin_moves_term_under_new_parent(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"parentId":"%s"}\
            """.formatted(SANIDADE))
    .when()
        .put(parentOf(HOSPITAIS))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  // Stubbing the exact call the controller must make — HOSPITAIS with a null parent — and
  // asserting the refusal that stub produces is what makes this about the null rather than
  // about the 204: a controller that dropped the null, passed some other id, or skipped
  // MoveTermo altogether would miss the stub and answer 204 instead of 404. Moving a term
  // out to the root is unexpressible otherwise, which is why the field carries no @NotNull.
  @Test
  void explicit_null_parent_reaches_the_use_case_as_move_to_the_root(RequestSpecification spec) {
    doThrow(new TermoNotFoundException(HOSPITAIS)).when(moveTermo).move(HOSPITAIS, null);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"parentId":null}\
                """)
        .when()
            .put(parentOf(HOSPITAIS));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void admin_moves_term_to_the_root_with_an_explicit_null_parent(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"parentId":null}\
            """)
    .when()
        .put(parentOf(HOSPITAIS))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  // The contract accepts an omitted parentId as the same request as an explicit null, since
  // Micronaut cannot tell them apart on a nullable component. Asserted so the equivalence is
  // proven rather than an accident of deserialization nobody checked.
  @Test
  void omitted_parent_moves_the_term_to_the_root_too(RequestSpecification spec) {
    doThrow(new TermoNotFoundException(HOSPITAIS)).when(moveTermo).move(HOSPITAIS, null);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{}")
        .when()
            .put(parentOf(HOSPITAIS));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void move_onto_unknown_parent_is_termo_not_found(RequestSpecification spec) {
    doThrow(new TermoNotFoundException(SANIDADE)).when(moveTermo).move(HOSPITAIS, SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"parentId":"%s"}\
                """.formatted(SANIDADE))
        .when()
            .put(parentOf(HOSPITAIS));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  @Test
  void move_creating_cycle_is_termo_cycle(RequestSpecification spec) {
    doThrow(new TermoCycleException(SANIDADE, HOSPITAIS))
        .when(moveTermo).move(SANIDADE, HOSPITAIS);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"parentId":"%s"}\
                """.formatted(HOSPITAIS))
        .when()
            .put(parentOf(SANIDADE));

    assertProblem(response, HttpStatus.CONFLICT, "urn:conxugal:problem-type:termo-cycle");
  }

  // The move is the one operation whose contract declares two different 409 types, so the
  // second one needs proving at the wire as much as the cycle does.
  @Test
  void move_colliding_with_sibling_name_is_duplicate_sibling_name(RequestSpecification spec) {
    doThrow(new DuplicateSiblingNameException("Hospitais", SANIDADE))
        .when(moveTermo).move(HOSPITAIS, SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"parentId":"%s"}\
                """.formatted(SANIDADE))
        .when()
            .put(parentOf(HOSPITAIS));

    assertProblem(
        response, HttpStatus.CONFLICT, "urn:conxugal:problem-type:duplicate-sibling-name");
  }

  @Test
  void admin_deletes_term(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(termo(SANIDADE))
    .then()
        .statusCode(HttpStatus.NO_CONTENT.getCode());
  }

  @Test
  void delete_of_unknown_term_is_termo_not_found(RequestSpecification spec) {
    doThrow(new TermoNotFoundException(SANIDADE)).when(deleteTermo).delete(SANIDADE);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .delete(termo(SANIDADE));

    assertProblem(response, HttpStatus.NOT_FOUND, "urn:conxugal:problem-type:termo-not-found");
  }

  // The two 409s are asserted side by side because sharing a status is the whole point: a
  // client can only tell a blocked delete from a cycle by the problem type, so a test that
  // checked them apart would pass even if both carried the same one.
  @Test
  void blocked_delete_and_cycle_conflict_under_different_problem_types(
      RequestSpecification spec) {
    doThrow(new TermoHasChildrenException(SANIDADE)).when(deleteTermo).delete(SANIDADE);
    doThrow(new TermoCycleException(SANIDADE, HOSPITAIS))
        .when(moveTermo).move(SANIDADE, HOSPITAIS);
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    Response blockedDelete =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .delete(termo(SANIDADE));
    Response cycle =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body(
                """
                {"parentId":"%s"}\
                """.formatted(HOSPITAIS))
        .when()
            .put(parentOf(SANIDADE));

    assertProblem(
        blockedDelete, HttpStatus.CONFLICT, "urn:conxugal:problem-type:termo-has-children");
    assertProblem(cycle, HttpStatus.CONFLICT, "urn:conxugal:problem-type:termo-cycle");
    // A client that falls back to the title rather than switching on the type still gets
    // two different messages, so neither refusal reads as a bare "Conflict".
    assertThat(blockedDelete.jsonPath().getString("title"))
        .isNotEqualTo(cycle.jsonPath().getString("title"));
  }

  @Test
  void user_role_is_forbidden_on_create(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"name":"Sanidade","parentId":null}\
            """)
    .when()
        .post(TERMOS)
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void user_role_is_forbidden_on_rename(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"name":"Sanidade"}\
            """)
    .when()
        .patch(termo(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void user_role_is_forbidden_on_move(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(
            """
            {"parentId":null}\
            """)
    .when()
        .put(parentOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void user_role_is_forbidden_on_delete(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .delete(termo(SANIDADE))
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_create(RequestSpecification spec) {
    given(spec)
        .body(
            """
            {"name":"Sanidade","parentId":null}\
            """)
    .when()
        .post(TERMOS)
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_rename(RequestSpecification spec) {
    given(spec)
        .body(
            """
            {"name":"Sanidade"}\
            """)
    .when()
        .patch(termo(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_move(RequestSpecification spec) {
    given(spec)
        .body(
            """
            {"parentId":null}\
            """)
    .when()
        .put(parentOf(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized_on_delete(RequestSpecification spec) {
    given(spec)
    .when()
        .delete(termo(SANIDADE))
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  private static String termo(TermoId termoId) {
    return "/api/admin/organos/taxonomia/termo/" + termoId;
  }

  private static String parentOf(TermoId termoId) {
    return termo(termoId) + "/parent";
  }

  private static void assertProblem(Response response, HttpStatus status, String problemType) {
    response.then()
        .statusCode(status.getCode())
        .contentType("application/problem+json");
    assertThat(response.jsonPath().getString("type")).isEqualTo(problemType);
    assertThat(response.jsonPath().getInt("status")).isEqualTo(status.getCode());
  }
}
