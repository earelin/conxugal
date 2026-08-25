package gal.conxugal.acceptance;

import static gal.conxugal.acceptance.support.AdminAccounts.field;
import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.AdminAccounts;
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

  /**
   * Repairs before it drives, not only after. Reaching the datastore first is what makes a run
   * that was pointed at one instance and another's datastore refuse here — while nothing has been
   * disabled yet — rather than in the teardown that was meant to be the way back. It also lifts an
   * instance that a previously failed run left with no enabled administrator, which is a state no
   * endpoint can leave.
   */
  @BeforeEach
  void restoreTheAdministratorAndLogIn() {
    ApplicationDatabase.enableAccount(ApplicationSession.ADMIN_EMAIL);
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
    JsonPath listing = AdminAccounts.listedBy(adminSession).jsonPath();
    List<String> enabledAdmins =
        listing.getList("findAll { it.role == 'ADMIN' && it.enabled }.email", String.class);
    // Asserted rather than assumed: with a second enabled administrator the refusal below would
    // never be reached and the scenario would pass without proving anything. The contract test
    // fuzzes account creation and its accounts cannot be deleted, so it is the likely way an
    // instance acquires one — hence a message that names the recovery rather than a bare diff.
    assertThat(enabledAdmins)
        .as(
            "this instance must hold exactly one enabled ADMIN; a contract-test run leaves more "
                + "behind, and accounts are never deleted — recreate the stack with "
                + "'docker compose --profile app down -v'")
        .containsExactly(ApplicationSession.ADMIN_EMAIL);
    String administratorId = listing.getString(field(ApplicationSession.ADMIN_EMAIL, "id"));

    Response refused = AdminAccounts.setEnabled(adminSession, administratorId, false);

    refused.then()
        .statusCode(409);
    // The problem type, not only the status: a conflict raised for any other reason would
    // otherwise read as this guard holding.
    assertThat(refused.jsonPath().getString("type"))
        .isEqualTo("urn:conxugal:problem-type:last-enabled-admin");
    JsonPath afterTheRefusal = AdminAccounts.listedBy(adminSession).jsonPath();
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
}
