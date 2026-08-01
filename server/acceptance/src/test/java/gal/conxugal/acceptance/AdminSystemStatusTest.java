package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.ApplicationSession;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Reads the packaged server's own account of its health. Only a genuinely running instance
 * can show the datastore probe reaching a real PostgreSQL rather than a stubbed port.
 */
class AdminSystemStatusTest {

  @Test
  void admin_reads_system_status_reporting_the_datastore_is_reachable() {
    String adminSession = ApplicationSession.logInAsAdmin();

    Response status = systemStatus(adminSession);

    status.then()
        .statusCode(200);
    JsonPath reported = status.jsonPath();
    assertThat(reported.getString("status")).isEqualTo("UP");
    assertThat(reported.getBoolean("datastore.reachable")).isTrue();
    assertThat(reported.getString("application.version")).isNotBlank();
    assertThat(reported.getString("runtime.javaVersion")).isNotBlank();
    assertThat(status.asString())
        .doesNotContain("jdbc:", "postgres", "password", "conxugal-dev-only");

    // A status cached at startup would satisfy every assertion above, so ask a second time:
    // only a snapshot assembled per request moves its instant on.
    JsonPath reportedAgain = systemStatus(adminSession).jsonPath();

    assertThat(Instant.parse(reportedAgain.getString("checkedAt")))
        .isAfter(Instant.parse(reported.getString("checkedAt")));
  }

  private static Response systemStatus(String adminSession) {
    return ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/system-status");
  }
}
