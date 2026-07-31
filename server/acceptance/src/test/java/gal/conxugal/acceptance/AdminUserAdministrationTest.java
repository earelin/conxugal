package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.ApplicationDatabase;
import gal.conxugal.acceptance.support.ApplicationSession;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the packaged server's administration API as an administrator would, proving the
 * account lifecycle holds end to end against a real datastore — a generated password that
 * actually signs its account in, a disabled account that actually cannot.
 */
class AdminUserAdministrationTest {

  private static final String CREATED_EMAIL_PATTERN = "acceptance-%@example.com";

  private String adminSession;
  private String newAccountEmail;

  @BeforeEach
  void logInAsAdministrator() {
    adminSession =
        ApplicationSession.logInOrFail(
            ApplicationSession.ADMIN_EMAIL, ApplicationSession.ADMIN_PASSWORD);
    newAccountEmail = "acceptance-%s@example.com".formatted(UUID.randomUUID());
  }

  @AfterEach
  void deleteTheAccountsThisScenarioCreated() {
    ApplicationDatabase.deleteAccounts(CREATED_EMAIL_PATTERN);
  }

  @Test
  void admin_creates_account_and_the_new_user_signs_in_with_the_generated_password() {
    Response created =
        ApplicationSession.authenticatedAs(adminSession)
            .body(
                """
                {"email":"%s","role":"USER"}\
                """.formatted(newAccountEmail))
        .when()
            .post("/api/admin/users");

    created.then().statusCode(201);
    assertThat(created.jsonPath().getString("email")).isEqualTo(newAccountEmail);
    assertThat(created.jsonPath().getString("role")).isEqualTo("USER");
    assertThat(created.jsonPath().getBoolean("enabled")).isTrue();
    assertThat(created.jsonPath().getString("createdAt")).isNotBlank();
    String initialPassword = created.jsonPath().getString("initialPassword");
    assertThat(initialPassword)
        .hasSizeGreaterThanOrEqualTo(16)
        .matches(".*[a-z].*")
        .matches(".*[A-Z].*")
        .matches(".*\\d.*")
        .matches(".*[^A-Za-z0-9].*");

    String newUserSession = ApplicationSession.logInOrFail(newAccountEmail, initialPassword);
    Response profile =
        ApplicationSession.authenticatedAs(newUserSession)
        .when()
            .get("/api/me");

    profile.then().statusCode(200);
    assertThat(profile.jsonPath().getString("email")).isEqualTo(newAccountEmail);
    assertThat(profile.jsonPath().getString("role")).isEqualTo("USER");
    assertThat(profile.jsonPath().getString("lastLoginAt")).isNotBlank();
  }

  @Test
  void admin_lists_accounts_including_the_new_one_that_never_logged_in() {
    String initialPassword = createAccount().jsonPath().getString("initialPassword");

    Response accounts =
        ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/users");

    accounts.then().statusCode(200);
    JsonPath listing = accounts.jsonPath();
    assertThat(listing.getList("email", String.class))
        .contains(ApplicationSession.ADMIN_EMAIL, newAccountEmail);
    assertThat(listing.getBoolean(newAccountField("enabled"))).isTrue();
    assertThat(listing.getString(newAccountField("createdAt"))).isNotBlank();
    assertThat(listing.getString(newAccountField("lastLoginAt"))).isNull();
    assertThat(accounts.asString())
        .doesNotContain(initialPassword, "initialPassword", "passwordHash");
  }

  @Test
  void admin_disables_account_denying_sign_in_and_re_enables_it() {
    Response created = createAccount();
    String initialPassword = created.jsonPath().getString("initialPassword");
    String accountId = created.jsonPath().getString("id");

    Response disabled = setEnabled(accountId, false);

    disabled.then().statusCode(200);
    assertThat(disabled.jsonPath().getBoolean("enabled")).isFalse();
    assertThat(ApplicationSession.logIn(newAccountEmail, initialPassword)).isEmpty();
    assertThat(listedAccountEmails()).contains(newAccountEmail);

    Response reEnabled = setEnabled(accountId, true);

    reEnabled.then().statusCode(200);
    assertThat(reEnabled.jsonPath().getBoolean("enabled")).isTrue();
    assertThat(ApplicationSession.logIn(newAccountEmail, initialPassword)).isNotEmpty();
  }

  @Test
  void user_role_is_denied_the_administration_api() {
    String userSession =
        ApplicationSession.logInOrFail(
            ApplicationSession.USER_EMAIL, ApplicationSession.USER_PASSWORD);

    ApplicationSession.authenticatedAs(userSession)
    .when()
        .get("/api/admin/users")
    .then()
        .statusCode(403);
  }

  private Response createAccount() {
    Response created =
        ApplicationSession.authenticatedAs(adminSession)
            .body(
                """
                {"email":"%s","role":"USER"}\
                """.formatted(newAccountEmail))
        .when()
            .post("/api/admin/users");
    created.then().statusCode(201);
    return created;
  }

  private Response setEnabled(String accountId, boolean enabled) {
    return ApplicationSession.authenticatedAs(adminSession)
        .body(
            """
            {"enabled":%s}\
            """.formatted(enabled))
    .when()
        .post("/api/admin/users/%s/enabled".formatted(accountId));
  }

  private List<String> listedAccountEmails() {
    return ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/users")
        .jsonPath()
            .getList("email", String.class);
  }

  private String newAccountField(String field) {
    return "find { it.email == '%s' }.%s".formatted(newAccountEmail, field);
  }
}
