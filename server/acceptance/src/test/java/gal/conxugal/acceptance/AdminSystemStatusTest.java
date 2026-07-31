package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import gal.conxugal.acceptance.support.ApplicationSession;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * Reads the packaged server's own account of its health. Only a genuinely running instance
 * can show the datastore probe reaching a real PostgreSQL rather than a stubbed port.
 */
class AdminSystemStatusTest {

  @Test
  void admin_reads_system_status_reporting_the_datastore_is_reachable() {
    String adminSession =
        ApplicationSession.logInOrFail(
            ApplicationSession.ADMIN_EMAIL, ApplicationSession.ADMIN_PASSWORD);

    Response status =
        ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/system-status");

    status.then().statusCode(200);
    JsonPath reported = status.jsonPath();
    assertThat(reported.getString("status")).isEqualTo("UP");
    assertThat(reported.getBoolean("datastore.reachable")).isTrue();
    assertThat(Instant.parse(reported.getString("checkedAt")))
        .isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    assertThat(reported.getString("application.version")).isNotBlank();
    assertThat(reported.getString("runtime.javaVersion")).isNotBlank();
    assertThat(reported.getLong("runtime.uptimeMillis")).isPositive();
    assertThat(status.asString())
        .doesNotContain("jdbc:", "postgres", "password", "conxugal-dev-only");
  }
}
