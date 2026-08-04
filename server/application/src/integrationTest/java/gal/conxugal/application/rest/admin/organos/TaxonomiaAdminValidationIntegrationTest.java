package gal.conxugal.application.rest.admin.organos;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedNewTerms")
  void create_is_refused(String reason, String body, RequestSpecification spec) {
    assertProblem(postTermos(spec, body)).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createTermo);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedNewNames")
  void rename_is_refused(String reason, String body, RequestSpecification spec) {
    assertProblem(patchTermo(spec, body)).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(renameTermo);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedMoves")
  void move_is_refused(String reason, String body, RequestSpecification spec) {
    assertProblem(putParent(spec, body)).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(moveTermo);
  }

  /** Padding is stripped, not refused — only a name that is *nothing but* blanks is refused. */
  @Test
  void create_with_name_padded_by_non_breaking_space_is_accepted(RequestSpecification spec) {
    when(createTermo.create(" Sanidade ", null))
        .thenReturn(new Termo(SANIDADE, "Sanidade", null));

    Response response = postTermos(spec, "{\"name\":\"\\u00a0Sanidade\\u00a0\",\"parentId\":null}");

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("name")).isEqualTo("Sanidade");
  }

  private static Stream<Arguments> refusedNewTerms() {
    return Stream.concat(
        refusedNames(",\"parentId\":null"),
        Stream.of(
            arguments("parent is not a UUID",
                """
                {"name":"Sanidade","parentId":"not-a-uuid"}\
                """),
            arguments("parent is not a string",
                """
                {"name":"Sanidade","parentId":7}\
                """),
            arguments("body is not an object", "[]")));
  }

  private static Stream<Arguments> refusedNewNames() {
    return refusedNames("");
  }

  private static Stream<Arguments> refusedMoves() {
    return Stream.of(
        arguments("parent is absent, which states nothing at all", "{}"),
        arguments("parent is not a UUID",
            """
            {"parentId":"not-a-uuid"}\
            """),
        arguments("parent is not a string",
            """
            {"parentId":7}\
            """));
  }

  /**
   * The name rules a create and a rename share, rendered into whichever body carries them: a
   * create declares the parent alongside, a rename carries the name alone.
   */
  private static Stream<Arguments> refusedNames(String rest) {
    return Stream.of(
        arguments("name is only ordinary spaces", "{\"name\":\"   \"%s}".formatted(rest)),
        arguments("name is only non-breaking spaces",
            "{\"name\":\"\\u00a0\\u2007\"%s}".formatted(rest)),
        arguments("name carries a NUL the datastore cannot hold",
            "{\"name\":\"Sanid\\u0000ade\"%s}".formatted(rest)),
        arguments("name carries a line break",
            "{\"name\":\"Sanidade\\ne Benestar\"%s}".formatted(rest)),
        arguments("name is longer than its column",
            "{\"name\":\"%s\"%s}".formatted("a".repeat(256), rest)),
        arguments("name is not a string", "{\"name\":false%s}".formatted(rest)),
        arguments("name is absent", "{%s}".formatted(rest.replaceFirst("^,", ""))));
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
