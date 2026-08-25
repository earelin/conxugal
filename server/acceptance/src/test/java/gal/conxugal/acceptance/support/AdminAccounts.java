package gal.conxugal.acceptance.support;

import io.restassured.response.Response;

/**
 * The administration API's account endpoints, as an outside caller reaches them. Scenarios that
 * drive different rules still read the same listing and toggle the same flag, so the requests live
 * here rather than being spelled out again in each of them.
 */
public final class AdminAccounts {

  private AdminAccounts() {
  }

  /** Every account the instance holds, enabled and disabled alike. */
  public static Response listedBy(String session) {
    return ApplicationSession.authenticatedAs(session)
        .when()
            .get("/api/admin/users");
  }

  /** Sets one account's enabled state, whether or not the instance will accept the change. */
  public static Response setEnabled(String session, String accountId, boolean enabled) {
    return ApplicationSession.authenticatedAs(session)
        .body(
            """
            {"enabled":%s}\
            """.formatted(enabled))
    .when()
        .post("/api/admin/users/%s/enabled".formatted(accountId));
  }

  /**
   * A GPath into the listing, reaching one account's field by the email that identifies it — the
   * listing is an array, and its order is not part of the contract, so a scenario cannot index in.
   */
  public static String field(String email, String name) {
    return "find { it.email == '%s' }.%s".formatted(email, name);
  }
}
