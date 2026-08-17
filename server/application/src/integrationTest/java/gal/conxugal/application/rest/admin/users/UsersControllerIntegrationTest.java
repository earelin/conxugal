package gal.conxugal.application.rest.admin.users;

import static gal.conxugal.application.http.error.support.AssertProblem.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import gal.conxugal.domain.user.UserId;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
        new UserId(UUID.randomUUID()), "nova@example.com", "stored-hash", Role.USER, true,
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
        new UserId(UUID.randomUUID()), "new.admin@example.com", "hashed-password", Role.ADMIN, true,
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

    assertProblem(response)
        .hasStatus(HttpStatus.CONFLICT)
        .hasType("urn:conxugal:problem-type:duplicate-email");
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
    UserId unknownId = new UserId(UUID.randomUUID());
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

  // --- What the edge refuses before a use case ever sees it -------------------
  // An email that is one, a role the enum names, and an enabled state that is a JSON boolean
  // rather than something readable as one. Each asserts the use case was never reached: a
  // request understood as something other than what it said would still have reached it.

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedNewAccounts")
  void create_is_refused(String reason, String body, RequestSpecification spec) {
    assertProblem(postCreate(spec, body)).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(createUser);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("acceptedAddresses")
  void create_accepts_an_awkward_address(String email, RequestSpecification spec) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    User created = new User(
        new UserId(UUID.randomUUID()), email, "hashed-password", Role.USER, true,
        Instant.parse("2026-07-18T09:30:00Z"));
    when(createUser.create(email, Role.USER))
        .thenReturn(new CreatedAccount(created, new GeneratedPassword("Tg7#kLp2Qw9$mZxR")));
    String sessionCookie = loginAs(spec, admin);

    Response response =
        given(spec)
            .header(HttpHeaders.COOKIE, sessionCookie)
            .body("{\"email\":\"%s\",\"role\":\"USER\"}".formatted(email))
        .when()
            .post("/api/admin/users");

    response.then().statusCode(HttpStatus.CREATED.getCode());
    assertThat(response.jsonPath().getString("email")).isEqualTo(email);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refusedEnabledStates")
  void setting_enabled_is_refused(String reason, String body, RequestSpecification spec) {
    assertProblem(postEnabled(spec, body)).hasStatus(HttpStatus.BAD_REQUEST);
    verifyNoInteractions(setUserEnabled);
  }

  private static Stream<Arguments> refusedNewAccounts() {
    return Stream.of(
        arguments("email is blank", "{\"email\":\"\",\"role\":\"USER\"}"),
        arguments("email is not an address", "{\"email\":\"not-an-email\",\"role\":\"USER\"}"),
        arguments("email is not a string", "{\"email\":42,\"role\":\"USER\"}"),
        arguments("email is absent", "{\"role\":\"USER\"}"),
        arguments("role is absent", "{\"email\":\"new.admin@conxugal.gal\"}"),
        arguments("role is outside the enum",
            "{\"email\":\"new.admin@conxugal.gal\",\"role\":\"WIZARD\"}"),
        arguments("body is not an object", "[]"),
        arguments("email is the address the contract test slipped through",
            "{\"email\":\"û@N\",\"role\":\"USER\"}"),
        arguments("email local part is not ASCII",
            "{\"email\":\"û@example.gal\",\"role\":\"USER\"}"),
        arguments("email domain carries no dot", "{\"email\":\"root@local\",\"role\":\"USER\"}"),
        arguments("email top-level domain is one letter",
            "{\"email\":\"a@b.c\",\"role\":\"USER\"}"),
        arguments("email local part opens with a dot",
            "{\"email\":\".a@example.gal\",\"role\":\"USER\"}"),
        arguments("email local part carries two dots",
            "{\"email\":\"a..b@example.gal\",\"role\":\"USER\"}"),
        arguments("email domain label closes with a hyphen",
            "{\"email\":\"a@ex-.gal\",\"role\":\"USER\"}"),
        arguments("email is longer than an address can be",
            "{\"email\":\"%s@example.gal\",\"role\":\"USER\"}".formatted("a".repeat(250))),
        arguments("email local part is longer than 64",
            "{\"email\":\"%s@example.gal\",\"role\":\"USER\"}".formatted("a".repeat(65))),
        arguments("email domain label is longer than 63",
            "{\"email\":\"a@%s.gal\",\"role\":\"USER\"}".formatted("b".repeat(64))),
        arguments("email closes with a line break",
            "{\"email\":\"a@example.gal\\n\",\"role\":\"USER\"}"));
  }

  // The plain address is admin_creates_user's; these are the awkward ones the contract's
  // pattern still admits, and that a generator working from it will send. The last two sit
  // exactly on the RFC length limits, which is where an off-by-one in the rule would show.
  // Without them an over-tightened rule would surface as a contract-test failure instead.
  private static Stream<Arguments> acceptedAddresses() {
    return Stream.of(
        arguments("first.last@example.gal"),
        arguments("a+tag@sub.example.co.uk"),
        arguments("weird!#$%&'*+-/=?^_`{|}~@example.gal"),
        arguments("%s@example.gal".formatted("a".repeat(64))),
        arguments("a@%s.gal".formatted("b".repeat(63))));
  }

  private static Stream<Arguments> refusedEnabledStates() {
    return Stream.of(
        arguments("enabled is absent", "{}"),
        arguments("enabled is a string readable as a boolean", "{\"enabled\":\"AAA\"}"),
        arguments("enabled is a number", "{\"enabled\":1}"),
        arguments("enabled is null", "{\"enabled\":null}"));
  }

  private Response postEnabled(RequestSpecification spec, String body) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    String sessionCookie = loginAs(spec, admin);

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(body)
    .when()
        .post("/api/admin/users/" + UUID.randomUUID() + "/enabled");
  }

  private Response postCreate(RequestSpecification spec, String body) {
    User admin = TestUserFactory.adminUser();
    seedUser(admin);
    String sessionCookie = loginAs(spec, admin);

    return given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
        .body(body)
    .when()
        .post("/api/admin/users");
  }
}
