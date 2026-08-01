package gal.conxugal.infrastructure.http.contratosdegalicia;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gal.conxugal.infrastructure.http.RetryAfterExceedsMaximumWaitException;
import gal.conxugal.infrastructure.http.UserAgent;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private static final String RETRY_AFTER = "Retry-After";
  private static final Duration RATE_LIMIT_REFRESH_PERIOD = Duration.ofMillis(500);
  private static final Duration RETRY_AFTER_MAXIMUM_WAIT = Duration.ofSeconds(3);
  private static final long HONOURED_RETRY_AFTER_SECONDS = 2;

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private WireMock source;

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
  void resetSource() {
    source = new WireMock(wireMock.getHost(), wireMock.getPort());
    source.resetMappings();
    source.resetRequests();
    source.resetScenarios();
  }

  @Test
  void retries_transient_failures_and_returns_the_eventual_success() {
    stubFailingOnceThenOk("transient", "GET", ORGANOS_PATH, 503, null);

    byte[] body = client.organos();

    assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("ok");
    assertThat(requestsTo("GET", ORGANOS_PATH)).hasSize(2);
  }

  /**
   * A 403 rather than a 404: Micronaut's declarative clients treat a 404 as "absent" and hand back
   * a null body instead of throwing, so a 404 would exercise that convention rather than this
   * policy's refusal to retry a permanent status.
   */
  @Test
  void does_not_retry_permanent_statuses() {
    stubStatus("GET", ORGANOS_PATH, 403, null);

    assertThatThrownBy(() -> client.organos()).isInstanceOf(HttpClientResponseException.class);

    assertThat(requestsTo("GET", ORGANOS_PATH)).hasSize(1);
  }

  @Test
  void does_not_retry_post_without_idempotent_declaration() {
    stubStatus("POST", SEARCH_PATH, 503, null);

    assertThatThrownBy(() -> client.search("obras"))
        .isInstanceOf(HttpClientResponseException.class);

    assertThat(requestsTo("POST", SEARCH_PATH)).hasSize(1);
  }

  @Test
  void retries_post_declared_idempotent() {
    stubFailingOnceThenOk("idempotent-post", "POST", SEARCH_PATH, 503, null);

    byte[] body = client.searchDeclaredIdempotent("obras");

    assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("ok");
    assertThat(requestsTo("POST", SEARCH_PATH)).hasSize(2);
  }

  @Test
  void waits_out_retry_after_within_the_configured_ceiling() {
    stubFailingOnceThenOk(
        "retry-after", "GET", ORGANOS_PATH, 503, String.valueOf(HONOURED_RETRY_AFTER_SECONDS));

    long startedAt = System.nanoTime();
    client.organos();
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(1900));
  }

  @Test
  void aborts_rather_than_waiting_out_retry_after_beyond_the_ceiling() {
    stubStatus("GET", ORGANOS_PATH, 503, String.valueOf(RETRY_AFTER_MAXIMUM_WAIT.toSeconds() + 1));

    assertThatThrownBy(() -> client.organos())
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);

    assertThat(requestsTo("GET", ORGANOS_PATH)).hasSize(1);
  }

  /**
   * The limiter's cycle clock starts when it is built, not when the first request goes out, so a
   * first request delayed by cold class loading lands late in its own cycle and the gap to the next
   * is correspondingly short. Measuring two requests that both follow a warm-up makes this a
   * property of the limiter rather than of how warm the JVM happened to be.
   */
  @Test
  void paces_requests_no_faster_than_the_configured_rate() {
    stubStatus("GET", ORGANOS_PATH, 200, null);
    client.organos();

    long startedAt = System.nanoTime();
    client.organos();
    client.organos();
    Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

    assertThat(elapsed).isGreaterThanOrEqualTo(RATE_LIMIT_REFRESH_PERIOD.minusMillis(100));
  }

  @Test
  void every_outgoing_request_carries_the_identifying_user_agent() {
    stubStatus("GET", ORGANOS_PATH, 200, null);

    client.organos();

    assertThat(requestsTo("GET", ORGANOS_PATH))
        .singleElement()
        .extracting(recorded -> recorded.getHeader("User-Agent"))
        .isEqualTo(applicationContext.getRequiredProperty(UserAgent.PROPERTY, String.class));
  }

  private void stubStatus(String method, String path, int status, String retryAfter) {
    source.register(
        request(method, urlEqualTo(path)).willReturn(response(status, retryAfter).withBody("ok")));
  }

  private void stubFailingOnceThenOk(
      String scenario, String method, String path, int failingStatus, String retryAfter) {
    source.register(
        request(method, urlEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs(STARTED)
            .willSetStateTo("failed-once")
            .willReturn(response(failingStatus, retryAfter)));
    source.register(
        request(method, urlEqualTo(path))
            .inScenario(scenario)
            .whenScenarioStateIs("failed-once")
            .willReturn(aResponse().withStatus(200).withBody("ok")));
  }

  private static ResponseDefinitionBuilder response(int status, String retryAfter) {
    ResponseDefinitionBuilder response = aResponse().withStatus(status);
    return retryAfter == null ? response : response.withHeader(RETRY_AFTER, retryAfter);
  }

  private List<LoggedRequest> requestsTo(String method, String path) {
    return source.find(newRequestPattern(RequestMethod.fromString(method), urlEqualTo(path)));
  }
}
