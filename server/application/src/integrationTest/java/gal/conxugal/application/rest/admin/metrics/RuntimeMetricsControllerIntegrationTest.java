package gal.conxugal.application.rest.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import gal.conxugal.domain.user.User;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@MicronautTest
@Property(name = "conxugal.metrics.stream.sample-interval", value = "1s")
@Property(name = "conxugal.metrics.stream.heartbeat-interval", value = "120ms")
class RuntimeMetricsControllerIntegrationTest extends AuthenticationTestSupport {

  private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);

  @Inject
  EmbeddedServer embeddedServer;

  @Inject
  RuntimeMetricsSource runtimeMetricsSource;

  private StreamingHttpClient streamingClient;

  @MockBean(RuntimeMetricsSource.class)
  RuntimeMetricsSource runtimeMetricsSourceMock() {
    return mock(RuntimeMetricsSource.class);
  }

  @BeforeEach
  void setUp() {
    streamingClient = StreamingHttpClient.create(embeddedServer.getURL());
    clearInvocations(runtimeMetricsSource);
  }

  @AfterEach
  void tearDown() throws InterruptedException {
    streamingClient.close();
    awaitStableInvocationCount(Duration.ofSeconds(5));
  }

  @Test
  void user_role_is_forbidden(RequestSpecification spec) {
    String sessionCookie = loginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/metrics")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void unauthenticated_caller_is_unauthorized(RequestSpecification spec) {
    given(spec)
    .when()
        .get("/api/admin/metrics")
    .then()
        .statusCode(HttpStatus.UNAUTHORIZED.getCode());
  }

  @Test
  void admin_receives_first_sample_without_waiting_for_the_interval(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = loginAs(spec, TestUserFactory.adminUser());

    StreamCollector collector = openStream(sessionCookie);
    try {
      collector.awaitText(text -> countDataFrames(text) >= 1, Duration.ofMillis(700));
      assertThat(collector.text()).contains("\"active\":1");
    } finally {
      collector.dispose();
    }
  }

  @Test
  void admin_receives_further_samples_reflecting_live_state(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample())
        .thenReturn(sample(1), sample(2), sample(3));
    String sessionCookie = loginAs(spec, TestUserFactory.adminUser());

    StreamCollector collector = openStream(sessionCookie);
    try {
      collector.awaitText(text -> countDataFrames(text) >= 3, Duration.ofSeconds(3));
      String text = collector.text();
      assertThat(text).contains("\"active\":1").contains("\"active\":2").contains("\"active\":3");
    } finally {
      collector.dispose();
    }
  }

  @Test
  void heartbeats_are_interleaved_between_samples(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = loginAs(spec, TestUserFactory.adminUser());

    StreamCollector collector = openStream(sessionCookie);
    try {
      collector.awaitText(text -> countDataFrames(text) >= 2, Duration.ofSeconds(2));
      List<String> frames = splitIntoFrames(collector.text());
      int firstDataFrame = indexOfFrame(frames, 0, f -> f.startsWith("data:"));
      int secondDataFrame = indexOfFrame(frames, firstDataFrame + 1, f -> f.startsWith("data:"));
      assertThat(firstDataFrame).isNotNegative();
      assertThat(secondDataFrame).isNotNegative();

      boolean heartbeatBetweenSamples = frames.subList(firstDataFrame + 1, secondDataFrame)
          .stream()
          .anyMatch(frame -> frame.contains(": heartbeat"));
      assertThat(heartbeatBetweenSamples).isTrue();
    } finally {
      collector.dispose();
    }
  }

  @Test
  void disconnecting_the_client_releases_the_subscription_and_its_timer(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = loginAs(spec, TestUserFactory.adminUser());

    StreamCollector collector = openStream(sessionCookie);
    collector.awaitText(text -> countDataFrames(text) >= 2, Duration.ofSeconds(2));
    collector.dispose();
    streamingClient.close();
    int invocationsAtDisconnect = awaitStableInvocationCount(Duration.ofSeconds(5));

    Thread.sleep(300);

    assertThat(mockingDetails(runtimeMetricsSource).getInvocations())
        .hasSize(invocationsAtDisconnect);
  }

  private int awaitStableInvocationCount(Duration timeout) throws InterruptedException {
    Duration pollGap = SAMPLE_INTERVAL.plusMillis(300);
    long deadline = System.currentTimeMillis() + timeout.toMillis();
    int previousCount = mockingDetails(runtimeMetricsSource).getInvocations().size();
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(pollGap.toMillis());
      int currentCount = mockingDetails(runtimeMetricsSource).getInvocations().size();
      if (currentCount == previousCount) {
        return currentCount;
      }
      previousCount = currentCount;
    }
    throw new AssertionError(
        "Invocation count never stabilised within %s, last seen: %d"
            .formatted(timeout, previousCount));
  }

  private StreamCollector openStream(String sessionCookie) {
    HttpRequest<?> request =
        HttpRequest.GET("/api/admin/metrics").header(HttpHeaders.COOKIE, sessionCookie);
    return new StreamCollector(Flux.from(streamingClient.dataStream(request)));
  }

  private String loginAs(RequestSpecification spec, User user) {
    seedUser(user);
    Response response =
        given(spec)
            .body("{\"username\":\"%s\",\"password\":\"%s\"}"
                .formatted(user.email(), user.passwordHash()))
        .when()
            .post("/login");
    return sessionCookieOf(response);
  }

  private static int countDataFrames(String text) {
    return (int) text.lines().filter(line -> line.startsWith("data:")).count();
  }

  private static int indexOfFrame(List<String> frames, int fromIndex, Predicate<String> match) {
    for (int i = fromIndex; i < frames.size(); i++) {
      if (match.test(frames.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private static List<String> splitIntoFrames(String text) {
    return List.of(text.split("\n{2,}"));
  }

  private static RuntimeMetrics sample(int active) {
    return new RuntimeMetrics(
        Instant.parse("2026-07-18T09:30:00Z"),
        new RuntimeMetrics.Jvm(
            536_870_912L, 2_147_483_648L, 100_663_296L, 42, 3_600_000L, 17L, 250L),
        new RuntimeMetrics.SystemLoad(0.35),
        new RuntimeMetrics.Http(15_230L, 12L),
        new RuntimeMetrics.DatastorePool(active, 7, 10));
  }

  private static final class StreamCollector {

    private final List<String> chunks = Collections.synchronizedList(new ArrayList<>());
    private final Disposable subscription;

    StreamCollector(Flux<ByteBuffer<?>> flux) {
      this.subscription = flux.subscribe(
          chunk -> chunks.add(new String(chunk.toByteArray(), StandardCharsets.UTF_8)));
    }

    String text() {
      synchronized (chunks) {
        return String.join("", chunks);
      }
    }

    void awaitText(Predicate<String> condition, Duration timeout) throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeout.toMillis();
      while (System.currentTimeMillis() < deadline) {
        if (condition.test(text())) {
          return;
        }
        Thread.sleep(20);
      }
      throw new AssertionError(
          "Condition not met within %s. Stream so far:%n%s".formatted(timeout, text()));
    }

    void dispose() {
      subscription.dispose();
    }
  }
}
