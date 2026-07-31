package gal.conxugal.infrastructure.http.contratosdegalicia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.infrastructure.http.RetryAfterExceedsMaximumWaitException;
import gal.conxugal.infrastructure.http.UserAgent;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

/**
 * Drives {@link ResilientTestClient} — a real Micronaut-generated declarative client — against a
 * stubbed source, so what is under test is the advice as the framework actually applies it.
 *
 * <p>The context is rebuilt per test because the circuit breaker is a singleton with memory: these
 * tests deliberately provoke failures, and a breaker shared across them would accumulate enough to
 * open, failing later tests for a reason that has nothing to do with what they assert.
 */
@MicronautTest(startApplication = false, transactional = false, rebuildContext = true)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilientClientIntegrationTest implements TestPropertyProvider {

  private static final String ORGANOS_PATH = "/organos";
  private static final String SEARCH_PATH = "/search";
  private static final Duration RATE_LIMIT_REFRESH_PERIOD = Duration.ofMillis(500);
  private static final Duration RETRY_AFTER_MAXIMUM_WAIT = Duration.ofSeconds(3);
  private static final long HONOURED_RETRY_AFTER_SECONDS = 2;

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private final HttpClient adminClient = HttpClient.newHttpClient();

  @Inject ResilientTestClient client;
  @Inject ApplicationContext applicationContext;

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!wireMock.isRunning()) {
      wireMock.start();
    }
    Map<String, String> properties = new LinkedHashMap<>();
    // The Órganos adapter still builds its own client from this; TASK-0008 retires it.
    properties.put("conxugal.contratosdegalicia.base-url", wireMock.getBaseUrl());
    properties.put("micronaut.http.services.contratosdegalicia.url", wireMock.getBaseUrl());
    properties.put("micronaut.http.services.contratosdegalicia.connect-timeout", "5s");
    properties.put("micronaut.http.services.contratosdegalicia.read-timeout", "10s");
    properties.put(
        "conxugal.contratosdegalicia.resilience.rate-limit-refresh-period",
        "%dms".formatted(RATE_LIMIT_REFRESH_PERIOD.toMillis()));
    properties.put("conxugal.contratosdegalicia.resilience.maximum-permit-wait", "10s");
    properties.put("conxugal.contratosdegalicia.resilience.retry-max-attempts", "3");
    properties.put("conxugal.contratosdegalicia.resilience.retry-base-delay", "10ms");
    properties.put(
        "conxugal.contratosdegalicia.resilience.retry-after-maximum-wait",
        "%ds".formatted(RETRY_AFTER_MAXIMUM_WAIT.toSeconds()));
    return properties;
  }

  @BeforeEach
  void resetStubs() throws Exception {
    admin("/__admin/reset", HttpRequest.BodyPublishers.noBody());
  }

  @AfterAll
  void closeAdminClient() {
    adminClient.close();
  }

  @Test
  void retries_transient_failures_and_returns_the_eventual_success() throws Exception {
    stubStatusThenSuccess("transient", "GET", ORGANOS_PATH, 503, null);

    byte[] body = client.organos();

    assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("ok");
    assertThat(recordedRequestCount("GET", ORGANOS_PATH)).isEqualTo(2);
  }

  /**
   * A 403 rather than a 404: Micronaut's declarative clients treat a 404 as "absent" and hand back
   * a null body instead of throwing, so a 404 would exercise that convention rather than this
   * policy's refusal to retry a permanent status.
   */
  @Test
  void does_not_retry_permanent_statuses() throws Exception {
    stubStatus("GET", ORGANOS_PATH, 403, null);

    assertThatThrownBy(() -> client.organos()).isInstanceOf(HttpClientResponseException.class);

    assertThat(recordedRequestCount("GET", ORGANOS_PATH)).isEqualTo(1);
  }

  @Test
  void does_not_retry_post_without_idempotent_declaration() throws Exception {
    stubStatus("POST", SEARCH_PATH, 503, null);

    assertThatThrownBy(() -> client.search("obras"))
        .isInstanceOf(HttpClientResponseException.class);

    assertThat(recordedRequestCount("POST", SEARCH_PATH)).isEqualTo(1);
  }

  @Test
  void retries_post_declared_idempotent() throws Exception {
    stubStatusThenSuccess("idempotent-post", "POST", SEARCH_PATH, 503, null);

    byte[] body = client.searchDeclaredIdempotent("obras");

    assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("ok");
    assertThat(recordedRequestCount("POST", SEARCH_PATH)).isEqualTo(2);
  }

  @Test
  void waits_out_retry_after_within_the_configured_ceiling() throws Exception {
    stubStatusThenSuccess(
        "retry-after", "GET", ORGANOS_PATH, 503, String.valueOf(HONOURED_RETRY_AFTER_SECONDS));

    long startedAt = System.nanoTime();
    client.organos();
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(1900));
  }

  @Test
  void aborts_rather_than_waiting_out_retry_after_beyond_the_ceiling() throws Exception {
    stubStatus(
        "GET", ORGANOS_PATH, 503, String.valueOf(RETRY_AFTER_MAXIMUM_WAIT.toSeconds() + 1));

    assertThatThrownBy(() -> client.organos())
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);

    assertThat(recordedRequestCount("GET", ORGANOS_PATH)).isEqualTo(1);
  }

  /**
   * The limiter's cycle clock starts when it is built, not when the first request goes out, so a
   * first request delayed by cold class loading lands late in its own cycle and the gap to the next
   * is correspondingly short. Measuring two requests that both follow a warm-up makes this a
   * property of the limiter rather than of how warm the JVM happened to be.
   */
  @Test
  void paces_requests_no_faster_than_the_configured_rate() throws Exception {
    stubStatus("GET", ORGANOS_PATH, 200, null);
    client.organos();

    long startedAt = System.nanoTime();
    client.organos();
    client.organos();
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(elapsed).isGreaterThanOrEqualTo(RATE_LIMIT_REFRESH_PERIOD.minusMillis(100));
  }

  @Test
  void every_outgoing_request_carries_the_identifying_user_agent() throws Exception {
    stubStatus("GET", ORGANOS_PATH, 200, null);

    client.organos();

    assertThat(requestJournal())
        .contains(applicationContext.getRequiredProperty(UserAgent.PROPERTY, String.class));
  }

  private void stubStatus(String method, String path, int status, String retryAfter)
      throws Exception {
    registerMapping(
        """
        { "request": { "method": "%s", "url": "%s" },
          "response": { "status": %d, "body": "ok"%s }
        }
        """
            .formatted(method, path, status, retryAfterHeader(retryAfter)));
  }

  private void stubStatusThenSuccess(
      String scenario, String method, String path, int failingStatus, String retryAfter)
      throws Exception {
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "Started",
          "newScenarioState": "failed-once",
          "request": { "method": "%s", "url": "%s" },
          "response": { "status": %d%s }
        }
        """
            .formatted(scenario, method, path, failingStatus, retryAfterHeader(retryAfter)));
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "failed-once",
          "request": { "method": "%s", "url": "%s" },
          "response": { "status": 200, "body": "ok" }
        }
        """
            .formatted(scenario, method, path));
  }

  private static String retryAfterHeader(String retryAfter) {
    return retryAfter == null ? "" : ", \"headers\": { \"Retry-After\": \"%s\" }".formatted(retryAfter);
  }

  private void registerMapping(String json) throws Exception {
    HttpResponse<String> response = admin("/__admin/mappings", HttpRequest.BodyPublishers.ofString(json));
    assertThat(response.statusCode()).isEqualTo(201);
  }

  private int recordedRequestCount(String method, String path) throws Exception {
    HttpResponse<String> response =
        admin(
            "/__admin/requests/count",
            HttpRequest.BodyPublishers.ofString(
                "{ \"method\": \"%s\", \"url\": \"%s\" }".formatted(method, path)));
    Matcher matcher = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(response.body());
    if (!matcher.find()) {
      throw new IllegalStateException(
          "Unexpected response from WireMock request count: %s".formatted(response.body()));
    }
    return Integer.parseInt(matcher.group(1));
  }

  private String requestJournal() throws Exception {
    return adminClient
        .send(
            HttpRequest.newBuilder(URI.create("%s/__admin/requests".formatted(wireMock.getBaseUrl())))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString())
        .body();
  }

  private HttpResponse<String> admin(String path, HttpRequest.BodyPublisher body) throws Exception {
    return adminClient.send(
        HttpRequest.newBuilder(URI.create("%s%s".formatted(wireMock.getBaseUrl(), path)))
            .POST(body)
            .header("Content-Type", "application/json")
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
