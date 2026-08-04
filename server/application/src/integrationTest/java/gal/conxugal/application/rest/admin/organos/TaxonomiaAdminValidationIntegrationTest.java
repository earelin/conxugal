package gal.conxugal.application.rest.admin.organos;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.taxonomia.CreateTermo;
import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.MoveTermo;
import gal.conxugal.domain.organo.taxonomia.RenameTermo;
import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSender;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The body each taxonomía write will accept, kept apart from the refusals the use cases
// raise: these never reach a use case at all, which is what every case here asserts. A name
// is a single-line label, non-blank once stripped, no longer than its column and free of a
// NUL the datastore cannot hold; a parent is a UUID, and a move must send one even to say
// "none".
@MicronautTest
class TaxonomiaAdminValidationIntegrationTest extends AuthenticationTestSupport {

  private static final String TERMOS = "/api/admin/organos/taxonomia/termos";
  private static final TermoId SANIDADE = new TermoId(UUID.randomUUID());
  private static final TermoId HOSPITAIS = new TermoId(UUID.randomUUID());

  @Inject
  CreateTermo createTermo;

  @Inject
  RenameTermo renameTermo;

  @Inject
  MoveTermo moveTermo;

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
  void create_with_name_that_is_only_non_breaking_space_is_bad_request(
      RequestSpecification spec) {
    Response response = postTermos(spec,
        """
        {"name":"\\u00a0\\u2007","parentId":null}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  /** Padding is stripped, not refused — only a name that is *nothing but* blanks is refused. */
  @Test
  void create_with_name_padded_by_non_breaking_space_is_accepted(RequestSpecification spec) {
    when(createTermo.create(" Sanidade ", null))
        .thenReturn(new Termo(SANIDADE, "Sanidade", null));

    Response response = postTermos(spec,
        """
        {"name":"\\u00a0Sanidade\\u00a0","parentId":null}\
        """);

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Sanidade");
  }

  @Test
  void create_with_name_carrying_nul_is_bad_request(RequestSpecification spec) {
    Response response = postTermos(spec,
        """
        {"name":"Sanid\\u0000ade","parentId":null}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @Test
  void create_with_name_carrying_line_break_is_bad_request(RequestSpecification spec) {
    Response response = postTermos(spec,
        """
        {"name":"Sanidade\\ne Benestar","parentId":null}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @Test
  void create_with_non_string_name_is_bad_request(RequestSpecification spec) {
    Response response = postTermos(spec,
        """
        {"name":false,"parentId":null}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @Test
  void create_without_name_is_bad_request(RequestSpecification spec) {
    Response response = postTermos(spec, "{}");

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @Test
  void create_with_malformed_parent_is_bad_request(RequestSpecification spec) {
    Response response = postTermos(spec,
        """
        {"name":"Sanidade","parentId":"not-a-uuid"}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @Test
  void rename_with_blank_name_is_bad_request(RequestSpecification spec) {
    Response response = patchTermo(spec,
        """
        {"name":"   "}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @Test
  void rename_with_name_longer_than_the_column_is_bad_request(RequestSpecification spec) {
    Response response = patchTermo(spec,
        """
        {"name":"%s"}\
        """.formatted("a".repeat(256)));

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @Test
  void rename_with_name_carrying_line_break_is_bad_request(RequestSpecification spec) {
    Response response = patchTermo(spec,
        """
        {"name":"Sanidade\\ne Benestar"}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @Test
  void rename_with_non_string_name_is_bad_request(RequestSpecification spec) {
    Response response = patchTermo(spec,
        """
        {"name":false}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @Test
  void rename_without_name_is_bad_request(RequestSpecification spec) {
    Response response = patchTermo(spec, "{}");

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @Test
  void move_with_malformed_parent_is_bad_request(RequestSpecification spec) {
    Response response = putParent(spec,
        """
        {"parentId":"not-a-uuid"}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(moveTermo);
  }

  @Test
  void move_with_non_string_parent_is_bad_request(RequestSpecification spec) {
    Response response = putParent(spec,
        """
        {"parentId":7}\
        """);

    assertProblem(response).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(moveTermo);
  }

  private Response postTermos(RequestSpecification spec, String body) {
    return asAdmin(spec, body).post(TERMOS);
  }

  private Response patchTermo(RequestSpecification spec, String body) {
    return asAdmin(spec, body).patch(termo(SANIDADE));
  }

  private Response putParent(RequestSpecification spec, String body) {
    return asAdmin(spec, body).put(parentOf(HOSPITAIS));
  }

  private RequestSender asAdmin(RequestSpecification spec, String body) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(body)
    .when();
  }

  private static String termo(TermoId termoId) {
    return "/api/admin/organos/taxonomia/termo/" + termoId;
  }

  private static String parentOf(TermoId termoId) {
    return termo(termoId) + "/parent";
  }
}
