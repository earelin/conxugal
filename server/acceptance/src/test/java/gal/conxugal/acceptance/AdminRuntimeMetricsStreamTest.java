package gal.conxugal.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.acceptance.support.ApplicationSession;
import gal.conxugal.acceptance.support.MetricsStream;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Watches the packaged server report on itself. Only a genuinely running instance can show
 * figures read from a live JVM and a real connection pool, moving as the instance works.
 */
class AdminRuntimeMetricsStreamTest {

  private static final String METRICS_PATH = "/api/admin/metrics";
  private static final String EVENT_STREAM = "text/event-stream";

  /**
   * The instance emits the first sample immediately and the rest on a cadence it alone chooses.
   * {@code PROMPTLY} is deliberately shorter than that cadence, so a first sample that waited
   * for a tick fails rather than passes; the later ones are given several cadences of slack.
   */
  private static final Duration PROMPTLY = Duration.ofSeconds(3);
  private static final Duration A_CADENCE_LATER = Duration.ofSeconds(20);
  private static final Duration LONG_ENOUGH_FOR_A_REPLAY = Duration.ofMillis(1500);

  private static final int REQUESTS_BETWEEN_SAMPLES = 3;

  @Test
  void admin_watches_runtime_metrics_move_as_the_instance_serves_requests() throws IOException {
    String adminSession = ApplicationSession.logInAsAdmin();

    try (MetricsStream metrics = MetricsStream.openAs(adminSession)) {
      String firstFrame = metrics.nextSampleFrame(PROMPTLY);
      JsonPath first = MetricsStream.valuesOf(firstFrame);

      assertThat(first.getLong("jvm.heapUsedBytes")).isPositive();
      assertThat(first.getInt("jvm.threadCount")).isPositive();
      assertThat(first.getLong("jvm.uptimeMillis")).isPositive();
      assertThat(first.getInt("datastorePool.max")).isPositive();

      for (int request = 0; request < REQUESTS_BETWEEN_SAMPLES; request++) {
        ApplicationSession.authenticatedAs(adminSession)
        .when()
            .get("/api/admin/system-status")
        .then()
            .statusCode(200);
      }

      String secondFrame = metrics.nextSampleFrame(A_CADENCE_LATER);
      JsonPath second = MetricsStream.valuesOf(secondFrame);

      assertThat(instantOf(second)).isAfter(instantOf(first));
      assertThat(second.getLong("jvm.uptimeMillis"))
          .isGreaterThan(first.getLong("jvm.uptimeMillis"));
      assertThat(second.getLong("http.requestCount"))
          .isGreaterThanOrEqualTo(first.getLong("http.requestCount") + REQUESTS_BETWEEN_SAMPLES);

      assertNoSecretIn(firstFrame);
      assertNoSecretIn(secondFrame);
    }
  }

  @Test
  void reconnecting_admin_is_given_the_current_sample_and_no_replay() throws IOException {
    String adminSession = ApplicationSession.logInAsAdmin();

    Instant beforeTheDrop;
    try (MetricsStream dropped = MetricsStream.openAs(adminSession)) {
      beforeTheDrop = instantOf(MetricsStream.valuesOf(dropped.nextSampleFrame(PROMPTLY)));
    }

    try (MetricsStream reconnected = MetricsStream.openAs(adminSession)) {
      Instant afterTheDrop =
          instantOf(MetricsStream.valuesOf(reconnected.nextSampleFrame(PROMPTLY)));

      assertThat(afterTheDrop).isAfterOrEqualTo(beforeTheDrop);
      // Nothing missed is re-sent: the cadence is far longer than this wait, so any frame
      // arriving now could only be a replay of what the dropped connection already saw.
      assertThat(reconnected.nextFrameWithin(LONG_ENOUGH_FOR_A_REPLAY)).isEmpty();
    }
  }

  @Test
  void user_role_is_denied_the_metrics_stream() {
    String userSession = ApplicationSession.logInAsUser();

    subscribing(ApplicationSession.authenticatedAs(userSession))
    .when()
        .get(METRICS_PATH)
    .then()
        .statusCode(403);
  }

  @Test
  void anonymous_caller_is_denied_the_metrics_stream() {
    subscribing(ApplicationSession.anonymous())
    .when()
        .get(METRICS_PATH)
    .then()
        .statusCode(401);
  }

  /** The datastore URL, user and password of the instance under test all read {@code conxugal}. */
  private static void assertNoSecretIn(String frame) {
    assertThat(frame)
        .doesNotContainIgnoringCase("jdbc:", "postgres", "password", "conxugal", "csrf");
  }

  private static RequestSpecification subscribing(RequestSpecification caller) {
    return caller.accept(EVENT_STREAM);
  }

  private static Instant instantOf(JsonPath sample) {
    return Instant.parse(sample.getString("timestamp"));
  }
}
