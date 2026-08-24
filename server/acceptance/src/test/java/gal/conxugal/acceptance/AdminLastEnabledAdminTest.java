package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.ApplicationDatabase;
import gal.conxugal.acceptance.support.ApplicationSession;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The rule that keeps the administration area reachable, driven against the packaged server. An
 * instance left with no enabled administrator could not be repaired through any endpoint — there
 * is no re-enable an anonymous caller may reach and no account deletion — so the refusal has to
 * hold across the whole chain, not only where a mocked use case can be made to throw.
 */
class AdminLastEnabledAdminTest {

  private String adminSession;

  @BeforeEach
  void logInAsAdministrator() {
    adminSession = ApplicationSession.logInAsAdmin();
  }

  /**
   * Runs whether or not the guard held: if it ever lets the disable through, every later scenario
   * against this instance would fail to log in as an administrator, and nothing the suite can
   * reach over HTTP would put that right.
   */
  @AfterEach
  void restoreTheAdministratorIfTheGuardLetTheDisableThrough() {
    ApplicationDatabase.enableAccount(ApplicationSession.ADMIN_EMAIL);
  }

  @Test
  void disabling_the_only_enabled_administrator_is_refused_and_the_area_stays_administrable() {
    JsonPath listing = listAccounts().jsonPath();
    List<String> enabledAdmins =
        listing.getList("findAll { it.role == 'ADMIN' && it.enabled }.email", String.class);
    // Asserted rather than assumed: were the instance to seed a second administrator, the
    // refusal below would never be reached and the scenario would pass without proving anything.
    assertThat(enabledAdmins).containsExactly(ApplicationSession.ADMIN_EMAIL);
    String administratorId = listing.getString(field(ApplicationSession.ADMIN_EMAIL, "id"));

    Response refused =
        ApplicationSession.authenticatedAs(adminSession)
            .body(
                """
                {"enabled":false}\
                """)
        .when()
            .post("/api/admin/users/%s/enabled".formatted(administratorId));

    refused.then()
        .statusCode(409);
    // The problem type, not only the status: a conflict raised for any other reason would
    // otherwise read as this guard holding.
    assertThat(refused.jsonPath().getString("type"))
        .isEqualTo("urn:conxugal:problem-type:last-enabled-admin");
    JsonPath afterTheRefusal = listAccounts().jsonPath();
    assertThat(afterTheRefusal.getBoolean(field(ApplicationSession.ADMIN_EMAIL, "enabled")))
        .isTrue();

    // The status code is not the rule: staying administrable is. Only a fresh sign-in reaching an
    // administration endpoint shows the credentials still open the area they guard.
    String sessionAfterTheRefusal = ApplicationSession.logInAsAdmin();

    ApplicationSession.authenticatedAs(sessionAfterTheRefusal)
    .when()
        .get("/api/admin/users")
    .then()
        .statusCode(200);
  }

  private Response listAccounts() {
    return ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/users");
  }

  private static String field(String email, String name) {
    return "find { it.email == '%s' }.%s".formatted(email, name);
  }
}
