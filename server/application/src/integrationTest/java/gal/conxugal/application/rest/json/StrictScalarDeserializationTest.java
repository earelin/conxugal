package gal.conxugal.application.rest.json;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.mockito.Mockito.mock;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.organo.taxonomia.CreateTermo;
import gal.conxugal.domain.organo.taxonomia.DeleteTermo;
import gal.conxugal.domain.organo.taxonomia.MoveTermo;
import gal.conxugal.domain.organo.taxonomia.RenameTermo;
import gal.conxugal.domain.user.CreateUser;
import gal.conxugal.domain.user.SetUserEnabled;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A request body read as the contract declares it. The mapper would otherwise coerce between
 * JSON types and read an absent property as a null one, so a body the contract forbids is not
 * rejected but quietly reinterpreted — {@code {"enabled": "AAA"}} as {@code false}, the
 * opposite of an administrator's intent. These pin the refusal at the transport boundary, and
 * pin its shape too: a refusal built this early is one the framework renders, which is exactly
 * where a problem document has been found missing a property the contract requires.
 */
@MicronautTest
class StrictScalarDeserializationTest extends AuthenticationTestSupport {

  private static final String TERMOS = "/api/admin/organos/taxonomia/termos";

  @MockBean(SetUserEnabled.class)
  SetUserEnabled setUserEnabledMock() {
    return mock(SetUserEnabled.class);
  }

  @MockBean(CreateUser.class)
  CreateUser createUserMock() {
    return mock(CreateUser.class);
  }

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
  void string_where_the_contract_declares_boolean_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, "/api/admin/users/%s/enabled".formatted(UUID.randomUUID()),
            """
            {"enabled":"AAA"}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void boolean_where_the_contract_declares_string_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, TERMOS,
            """
            {"name":false,"parentId":null}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void object_where_the_contract_declares_uuid_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, TERMOS,
            """
            {"name":"Sanidade","parentId":{}}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void string_that_is_not_uuid_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, TERMOS,
            """
            {"name":"Sanidade","parentId":"not-a-uuid"}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void value_outside_the_declared_enum_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, "/api/admin/users",
            """
            {"email":"new@conxugal.gal","role":"WIZARD"}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void body_that_is_not_an_object_is_refused(RequestSpecification spec) {
    assertProblem(post(spec, TERMOS, "[]")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  /** PostgreSQL cannot hold a NUL in a text column, so the edge turns it away as a bad request. */
  @Test
  void name_carrying_the_nul_character_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, TERMOS,
            """
            {"name":"Sanid\\u0000ade","parentId":null}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  /** A term's name is a label, and a label does not run to a second line. */
  @Test
  void name_carrying_line_break_is_refused(RequestSpecification spec) {
    assertProblem(
        post(spec, TERMOS,
            """
            {"name":"Sanidade\\ne Benestar","parentId":null}\
            """))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  private Response post(RequestSpecification spec, String path, String body) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(body)
    .when()
        .post(path);
  }
}
