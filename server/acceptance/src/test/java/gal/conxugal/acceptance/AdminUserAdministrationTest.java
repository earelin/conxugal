package gal.conxugal.acceptance;

import static gal.conxugal.acceptance.support.AdminAccounts.field;
import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.AdminAccounts;
import gal.conxugal.acceptance.support.ApplicationDatabase;
import gal.conxugal.acceptance.support.ApplicationSession;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
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

  private static final String REFUSED_LOGIN_LOCATION = "/login?error=true";

  private String adminSession;
  private String newAccountEmail;

  @BeforeEach
  void logInAsAdministrator() {
    adminSession = ApplicationSession.logInAsAdmin();
    newAccountEmail = "acceptance-%s@example.com".formatted(UUID.randomUUID());
  }

  @AfterEach
  void deleteTheAccountThisScenarioCreated() {
    ApplicationDatabase.deleteAccount(newAccountEmail);
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

    created.then()
        .statusCode(201);
    assertThat(created.jsonPath().getString("email")).isEqualTo(newAccountEmail);
    assertThat(created.jsonPath().getString("role")).isEqualTo("USER");
    assertThat(created.jsonPath().getBoolean("enabled")).isTrue();
    assertThat(created.jsonPath().getString("createdAt")).isNotBlank();
    String initialPassword = created.jsonPath().getString("initialPassword");
    assertThat(initialPassword).matches("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{16,}");

    String newUserSession = ApplicationSession.logInOrFail(newAccountEmail, initialPassword);
    Response profile =
        ApplicationSession.authenticatedAs(newUserSession)
        .when()
            .get("/api/me");

    profile.then()
        .statusCode(200);
    assertThat(profile.jsonPath().getString("email")).isEqualTo(newAccountEmail);
    assertThat(profile.jsonPath().getString("role")).isEqualTo("USER");
    assertThat(profile.jsonPath().getString("lastLoginAt")).isNotBlank();
  }

  @Test
  void creating_an_account_with_an_existing_email_is_refused_and_the_original_is_unchanged() {
    Response original = createAccount();
    String originalId = original.jsonPath().getString("id");
    String originalPassword = original.jsonPath().getString("initialPassword");
    // Read back from the listing rather than reused from the creation response: that response
    // carries the clock's nanoseconds, while every later read carries what the datastore kept,
    // rounded to microseconds. Comparing the two would fail on the rounding, not on a change.
    String originalCreatedAt =
        listAccounts().jsonPath().getString(field(newAccountEmail, "createdAt"));

    // A different role from the original, so an account quietly overwritten rather than left
    // alone shows up in the listing below instead of reading exactly like a refusal that worked.
    Response refused =
        ApplicationSession.authenticatedAs(adminSession)
            .body(
                """
                {"email":"%s","role":"ADMIN"}\
                """.formatted(newAccountEmail))
        .when()
            .post("/api/admin/users");

    refused.then()
        .statusCode(409);
    // The problem type, not only the status: a conflict raised for any other reason would
    // otherwise read as the uniqueness rule holding.
    assertThat(refused.jsonPath().getString("type"))
        .isEqualTo("urn:conxugal:problem-type:duplicate-email");
    JsonPath listing = listAccounts().jsonPath();
    assertThat(listing.getList("findAll { it.email == '%s' }".formatted(newAccountEmail)))
        .hasSize(1);
    assertThat(listing.getString(field(newAccountEmail, "id"))).isEqualTo(originalId);
    assertThat(listing.getString(field(newAccountEmail, "role"))).isEqualTo("USER");
    assertThat(listing.getString(field(newAccountEmail, "createdAt")))
        .isEqualTo(originalCreatedAt);

    // The row surviving is only half of it: a refusal that had rotated the credential would
    // leave the listing identical and still have locked the account's holder out.
    String sessionOfTheUntouchedAccount =
        ApplicationSession.logInOrFail(newAccountEmail, originalPassword);

    ApplicationSession.authenticatedAs(sessionOfTheUntouchedAccount)
    .when()
        .get("/api/me")
    .then()
        .statusCode(200);
  }

  @Test
  void admin_lists_accounts_including_the_new_one_that_never_logged_in() {
    String initialPassword = createAccount().jsonPath().getString("initialPassword");

    Response accounts = listAccounts();

    accounts.then()
        .statusCode(200);
    JsonPath listing = accounts.jsonPath();
    assertThat(listing.getList("email", String.class))
        .contains(ApplicationSession.ADMIN_EMAIL, newAccountEmail);
    assertThat(listing.getBoolean(field(newAccountEmail, "enabled"))).isTrue();
    assertThat(listing.getString(field(newAccountEmail, "createdAt"))).isNotBlank();
    assertThat(listing.getString(field(newAccountEmail, "lastLoginAt"))).isNull();
    // The administrator this scenario logged in with has a last login, so an absent value
    // above means "never signed in" rather than a field that has quietly gone missing.
    assertThat(listing.getString(field(ApplicationSession.ADMIN_EMAIL, "lastLoginAt")))
        .isNotBlank();
    assertThat(accounts.asString())
        .doesNotContain(initialPassword, "initialPassword", "passwordHash");
  }

  @Test
  void admin_disables_account_denying_sign_in_and_re_enables_it() {
    Response created = createAccount();
    String initialPassword = created.jsonPath().getString("initialPassword");
    String accountId = created.jsonPath().getString("id");

    Response disabled = setEnabled(accountId, false);

    disabled.then()
        .statusCode(200);
    assertThat(disabled.jsonPath().getBoolean("enabled")).isFalse();
    Response refused = ApplicationSession.logInResponse(newAccountEmail, initialPassword);
    refused.then()
        .statusCode(303)
        .header("Location", REFUSED_LOGIN_LOCATION);
    assertThat(ApplicationSession.sessionCookieOf(refused)).isEmpty();
    JsonPath whileDisabled = listAccounts().jsonPath();
    assertThat(whileDisabled.getList("email", String.class)).contains(newAccountEmail);
    assertThat(whileDisabled.getBoolean(field(newAccountEmail, "enabled"))).isFalse();

    Response reEnabled = setEnabled(accountId, true);

    reEnabled.then()
        .statusCode(200);
    assertThat(reEnabled.jsonPath().getBoolean("enabled")).isTrue();
    String recoveredSession = ApplicationSession.logInOrFail(newAccountEmail, initialPassword);
    Response recoveredProfile =
        ApplicationSession.authenticatedAs(recoveredSession)
        .when()
            .get("/api/me");

    recoveredProfile.then()
        .statusCode(200);
    assertThat(recoveredProfile.jsonPath().getString("email")).isEqualTo(newAccountEmail);
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
    created.then()
        .statusCode(201);
    return created;
  }

  private Response setEnabled(String accountId, boolean enabled) {
    return AdminAccounts.setEnabled(adminSession, accountId, enabled);
  }

  private Response listAccounts() {
    return AdminAccounts.listedBy(adminSession);
  }
}
