package gal.conxugal.acceptance;

import gal.conxugal.acceptance.support.ApplicationSession;
import org.junit.jupiter.api.Test;

/**
 * The administration API's gate as the packaged application actually enforces it, through
 * the security configuration a deployment ships with rather than one a test profile has had
 * a chance to relax.
 */
class AdminApiAccessControlTest {

  @Test
  void anonymous_caller_is_denied_the_administration_api() {
    ApplicationSession.anonymous()
    .when()
        .get("/api/admin/users")
    .then()
        .statusCode(401);
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
}
