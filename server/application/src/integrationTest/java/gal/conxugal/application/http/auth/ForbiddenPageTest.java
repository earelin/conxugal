package gal.conxugal.application.http.auth;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.RequestThreadRecorder;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.security.csrf.CsrfConfiguration;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "micronaut.security.redirect.enabled", value = "false")
class ForbiddenPageTest extends AuthenticationTestSupport {

  @Inject
  CsrfConfiguration csrfConfiguration;

  @Inject
  RequestThreadRecorder requestThreadRecorder;

  @BeforeEach
  void setUp() {
    seedUser(
        new User(UUID.randomUUID(), "user@example.com", "user-password", Role.USER, true,
            Instant.parse("2026-01-01T00:00:00Z")));
  }

  @Test
  void authenticated_visitor_sees_the_forbidden_page(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/forbidden");

    response
        .then()
            .statusCode(HttpStatus.OK.getCode());
    String body = response.getBody().asString();
    assertThat(body).contains("Acceso denegado");
    assertThat(body).contains("A túa conta non ten permisos para acceder a esta área.");
    assertThat(body).contains("name=\"csrfToken\"");
  }

  @Test
  void serves_the_forbidden_page_on_virtual_threads_not_the_event_loop(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/forbidden");

    assertThat(requestThreadRecorder.lastRequestThread().isVirtual()).isTrue();
  }

  @Test
  void submitting_the_pechar_sesion_form_logs_out_the_session(RequestSpecification spec) {
    String sessionCookie = login(spec, "user@example.com", "user-password");

    Response forbiddenPage =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/forbidden");
    String csrfCookieHeader = csrfCookieHeaderOf(forbiddenPage);
    String csrfToken = csrfCookieHeader.substring(csrfCookieHeader.indexOf('=') + 1);

    Response logoutResponse =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie + "; " + csrfCookieHeader)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .formParam("csrfToken", csrfToken)
        .when()
            .post("/logout");
    logoutResponse
        .then()
            .statusCode(HttpStatus.OK.getCode());

    Response forbiddenAfterLogout =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/forbidden");
    assertThat(forbiddenAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
  }

  private String csrfCookieHeaderOf(Response response) {
    String cookieName = csrfConfiguration.getCookieName();
    return response.getHeaders().getValues(HttpHeaders.SET_COOKIE).stream()
        .filter(header -> header.startsWith(cookieName + "="))
        .findFirst()
        .orElseThrow()
        .split(";", 2)[0];
  }

  private String login(RequestSpecification spec, String email, String password) {
    Response response =
        given(spec)
            .body("{\"username\":\"" + email + "\",\"password\":\"" + password + "\"}")
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }
}
