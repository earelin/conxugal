package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResilientHttpClientIntegrationTest {

  private static final String PATH = "/organos";

  @Container
  static WireMockContainer wireMock = new WireMockContainer(WireMockContainer.OFFICIAL_IMAGE_NAME);

  private final java.net.http.HttpClient adminClient = java.net.http.HttpClient.newHttpClient();
  private final List<HttpClient> createdClients = new ArrayList<>();

  @BeforeEach
  void resetStubs() throws Exception {
    java.net.http.HttpResponse<Void> response =
        adminClient.send(
            java.net.http.HttpRequest.newBuilder(
                    URI.create("%s/__admin/reset".formatted(wireMock.getBaseUrl())))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build(),
            java.net.http.HttpResponse.BodyHandlers.discarding());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @AfterEach
  void closeCreatedClients() {
    createdClients.forEach(HttpClient::close);
    createdClients.clear();
  }

  @Test
  void transient_failure_followed_by_success_eventually_returns_the_response() throws Exception {
    stubStatusThenSuccess("transient-failure", 503);
    ResilientHttpClient client = client(fastSettings());

    String result = client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);

    assertThat(result).isEqualTo("ok");
  }

  @Test
  void connection_reset_followed_by_success_eventually_returns_the_response() throws Exception {
    stubFaultThenSuccess("connection-reset", "CONNECTION_RESET_BY_PEER");
    ResilientHttpClient client = client(fastSettings());

    String result = client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);

    assertThat(result).isEqualTo("ok");
  }

  @Test
  void honors_retry_after_in_delay_seconds_form() throws Exception {
    stubStatusWithRetryAfterThenSuccess("retry-after-seconds", 429, "1");
    ResilientHttpClient client = client(fastSettings());

    Instant start = Instant.now();
    String result = client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(result).isEqualTo("ok");
    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void honors_retry_after_in_http_date_form() throws Exception {
    String retryAt =
        ZonedDateTime.now(ZoneOffset.UTC)
            .plusSeconds(2)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    stubStatusWithRetryAfterThenSuccess("retry-after-date", 503, retryAt);
    ResilientHttpClient client = client(fastSettings());

    Instant start = Instant.now();
    String result = client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(result).isEqualTo("ok");
    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void retry_after_beyond_the_configured_maximum_aborts_the_call() throws Exception {
    stubStatusWithRetryAfter(429, "3600");
    ResilientHttpClientSettings settings =
        new FixedResilientHttpClientSettings(
            wireMock.getBaseUrl(),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofMillis(50),
            Duration.ofSeconds(5),
            1,
            3,
            Duration.ofMillis(50),
            Duration.ofSeconds(1),
            50,
            Duration.ofSeconds(5));
    ResilientHttpClient client = client(settings);

    assertThatThrownBy(
            () -> client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true))
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class);
    assertThat(recordedRequestCount()).isEqualTo(1);
  }

  @Test
  void pacing_holds_across_calls_with_no_failures_involved() throws Exception {
    stubAlwaysOk();
    ResilientHttpClientSettings settings =
        new FixedResilientHttpClientSettings(
            wireMock.getBaseUrl(),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3),
            1,
            1,
            Duration.ofMillis(50),
            Duration.ofSeconds(5),
            50,
            Duration.ofSeconds(5));
    ResilientHttpClient client = client(settings);

    Instant start = Instant.now();
    client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);
    client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void pacing_holds_across_retried_attempts_as_well_as_the_first_attempt() throws Exception {
    stubStatusThenSuccess("pacing-retry", 503);
    ResilientHttpClientSettings settings =
        new FixedResilientHttpClientSettings(
            wireMock.getBaseUrl(),
            Duration.ofSeconds(2),
            Duration.ofSeconds(2),
            Duration.ofSeconds(1),
            Duration.ofSeconds(3),
            1,
            2,
            Duration.ofMillis(10),
            Duration.ofSeconds(5),
            50,
            Duration.ofSeconds(5));
    ResilientHttpClient client = client(settings);

    Instant start = Instant.now();
    String result = client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);
    Duration elapsed = Duration.between(start, Instant.now());

    assertThat(result).isEqualTo("ok");
    assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void every_outgoing_request_carries_the_identifying_user_agent() throws Exception {
    stubAlwaysOk();
    ResilientHttpClient client = client(fastSettings());

    client.exchange(HttpRequest.GET(PATH), this::mapToBody, body -> true);

    assertThat(requestJournal()).contains(ResilientHttpClient.USER_AGENT_VALUE);
  }

  private String mapToBody(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  // Closed in closeCreatedClients() via the createdClients registry, not here.
  @SuppressWarnings("PMD.CloseResource")
  private ResilientHttpClient client(ResilientHttpClientSettings settings) throws Exception {
    HttpClient rawClient = HttpClient.create(URI.create(wireMock.getBaseUrl()).toURL());
    createdClients.add(rawClient);
    return new ResilientHttpClient("wiremock-test", rawClient.toBlocking(), settings);
  }

  private ResilientHttpClientSettings fastSettings() {
    return new FixedResilientHttpClientSettings(
        wireMock.getBaseUrl(),
        Duration.ofSeconds(2),
        Duration.ofSeconds(2),
        Duration.ofMillis(50),
        Duration.ofSeconds(5),
        1,
        3,
        Duration.ofMillis(50),
        Duration.ofSeconds(5),
        50,
        Duration.ofSeconds(5));
  }

  private void stubStatusThenSuccess(String scenario, int failingStatus) throws Exception {
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "Started",
          "newScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "status": %d }
        }
        """
            .formatted(scenario, PATH, failingStatus));
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "status": 200, "body": "ok" }
        }
        """
            .formatted(scenario, PATH));
  }

  private void stubFaultThenSuccess(String scenario, String fault) throws Exception {
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "Started",
          "newScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "fault": "%s" }
        }
        """
            .formatted(scenario, PATH, fault));
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "status": 200, "body": "ok" }
        }
        """
            .formatted(scenario, PATH));
  }

  private void stubStatusWithRetryAfterThenSuccess(
      String scenario, int failingStatus, String retryAfter) throws Exception {
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "Started",
          "newScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "status": %d, "headers": { "Retry-After": "%s" } }
        }
        """
            .formatted(scenario, PATH, failingStatus, retryAfter));
    registerMapping(
        """
        { "scenarioName": "%s",
          "requiredScenarioState": "failed-once",
          "request": { "method": "GET", "url": "%s" },
          "response": { "status": 200, "body": "ok" }
        }
        """
            .formatted(scenario, PATH));
  }

  private void stubStatusWithRetryAfter(int status, String retryAfter) throws Exception {
    registerMapping(
        """
        { "request": { "method": "GET", "url": "%s" },
          "response": { "status": %d, "headers": { "Retry-After": "%s" } }
        }
        """
            .formatted(PATH, status, retryAfter));
  }

  private void stubAlwaysOk() throws Exception {
    registerMapping(
        """
        { "request": { "method": "GET", "url": "%s" },
          "response": { "status": 200, "body": "ok" }
        }
        """
            .formatted(PATH));
  }

  private void registerMapping(String mappingJson) throws Exception {
    java.net.http.HttpResponse<String> response =
        adminClient.send(
            java.net.http.HttpRequest.newBuilder(
                    URI.create("%s/__admin/mappings".formatted(wireMock.getBaseUrl())))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mappingJson))
                .header("Content-Type", "application/json")
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
  }

  private int recordedRequestCount() throws Exception {
    String body = "{ \"method\": \"GET\", \"url\": \"%s\" }".formatted(PATH);
    java.net.http.HttpResponse<String> response =
        adminClient.send(
            java.net.http.HttpRequest.newBuilder(
                    URI.create("%s/__admin/requests/count".formatted(wireMock.getBaseUrl())))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    Matcher matcher = Pattern.compile("\"count\"\\s*:\\s*(\\d+)").matcher(response.body());
    if (!matcher.find()) {
      throw new IllegalStateException(
          "Unexpected response from WireMock request count: %s".formatted(response.body()));
    }
    return Integer.parseInt(matcher.group(1));
  }

  private String requestJournal() throws Exception {
    java.net.http.HttpResponse<String> response =
        adminClient.send(
            java.net.http.HttpRequest.newBuilder(
                    URI.create("%s/__admin/requests".formatted(wireMock.getBaseUrl())))
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
    return response.body();
  }
}
