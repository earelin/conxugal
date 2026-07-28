package gal.conxugal.application.rest.admin.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.user.CreateUser;
import gal.conxugal.domain.user.CreatedAccount;
import gal.conxugal.domain.user.DuplicateEmailException;
import gal.conxugal.domain.user.GeneratedPassword;
import gal.conxugal.domain.user.LastEnabledAdminException;
import gal.conxugal.domain.user.Role;
import gal.conxugal.domain.user.SetUserEnabled;
import gal.conxugal.domain.user.User;
import gal.conxugal.domain.user.UserNotFoundException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// CreateUser and SetUserEnabled are both @Transactional and need a real datasource, which
// application-test.yml deliberately disables (this suite mocks the repository and needs no
// live database) — so both use cases are mocked here directly rather than exercised for real.
@MicronautTest
class UsersControllerIntegrationTest extends AuthenticationTestSupport {

  @Inject
  SetUserEnabled setUserEnabled;

  @Inject
  CreateUser createUser;

  @MockBean(SetUserEnabled.class)
  SetUserEnabled setUserEnabledMock() {
    return mock(SetUserEnabled.class);
  }

  @MockBean(CreateUser.class)
  CreateUser createUserMock() {
    return mock(CreateUser.class);
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    seedUser(TestUserFactory.normalUser());
    String sessionCookie = loginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/users")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/admin/users")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void admin_lists_accounts_including_one_never_logged_in(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    User neverLoggedIn = new User(
        UUID.randomUUID(), "nova@example.com", "stored-hash", Role.USER, true,
        Instant.parse("2026-01-01T00:00:00Z"));
    when(userRepository.findAll()).thenReturn(List.of(admin, neverLoggedIn));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
        .when()
            .get("/api/admin/users");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getList("email", String.class))
        .containsExactlyInAnyOrder(admin.email(), "nova@example.com");
    assertThat(response.jsonPath().getString("find { it.email == 'nova@example.com' }.lastLoginAt"))
        .isNull();
  }

  @Test
  void admin_creates_user(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    User created = new User(
        UUID.randomUUID(), "new.admin@example.com", "hashed-password", Role.ADMIN, true,
        Instant.parse("2026-07-18T09:30:00Z"));
    GeneratedPassword initialPassword = new GeneratedPassword("Tg7#kLp2Qw9$mZxR");
    when(createUser.create("new.admin@example.com", Role.ADMIN))
        .thenReturn(new CreatedAccount(created, initialPassword));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{\"email\":\"new.admin@example.com\",\"role\":\"ADMIN\"}")
        .when()
            .post("/api/admin/users");

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("email")).isEqualTo("new.admin@example.com");
    assertThat(response.jsonPath().getString("initialPassword")).isEqualTo("Tg7#kLp2Qw9$mZxR");
  }

  @Test
  void create_with_existing_email_is_conflict(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    when(createUser.create("ana@example.com", Role.USER))
        .thenThrow(new DuplicateEmailException("ana@example.com"));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{\"email\":\"ana@example.com\",\"role\":\"USER\"}")
        .when()
            .post("/api/admin/users");

    response.then()
        .statusCode(HttpStatus.CONFLICT.getCode())
        .contentType("application/problem+json");
  }

  @Test
  void create_with_blank_email_is_bad_request(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    String sessionCookie = loginAs(spec, admin);

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{\"email\":\"\",\"role\":\"USER\"}")
    .when()
        .post("/api/admin/users")
    .then()
        .statusCode(HttpStatus.BAD_REQUEST.getCode());
  }

  @Test
  void admin_disables_user_account(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    User target = TestUserFactory.normalUser();
    User disabled = new User(
        target.id(), target.email(), target.passwordHash(), target.role(), false,
        target.createdAt());
    when(setUserEnabled.setEnabled(target.id(), false)).thenReturn(disabled);
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{\"enabled\": false}")
        .when()
            .post("/api/admin/users/" + target.id() + "/enabled");

    response.then().statusCode(HttpStatus.OK.getCode());
    assertThat(response.jsonPath().getBoolean("enabled")).isFalse();
  }

  @Test
  void disabling_last_admin_is_conflict(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    when(setUserEnabled.setEnabled(admin.id(), false))
        .thenThrow(new LastEnabledAdminException(admin.id()));
    String sessionCookie = loginAs(spec, admin);

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{\"enabled\": false}")
    .when()
        .post("/api/admin/users/" + admin.id() + "/enabled")
    .then()
        .statusCode(HttpStatus.CONFLICT.getCode());
  }

  @Test
  void enabling_an_unknown_id_is_not_found(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    UUID unknownId = UUID.randomUUID();
    when(setUserEnabled.setEnabled(unknownId, true))
        .thenThrow(new UserNotFoundException(unknownId));
    String sessionCookie = loginAs(spec, admin);

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{\"enabled\": true}")
    .when()
        .post("/api/admin/users/" + unknownId + "/enabled")
    .then()
        .statusCode(HttpStatus.NOT_FOUND.getCode());
  }

  @Test
  void missing_enabled_field_is_bad_request(RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    String sessionCookie = loginAs(spec, admin);

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body("{}")
    .when()
        .post("/api/admin/users/" + UUID.randomUUID() + "/enabled")
    .then()
        .statusCode(HttpStatus.BAD_REQUEST.getCode());
  }
}
