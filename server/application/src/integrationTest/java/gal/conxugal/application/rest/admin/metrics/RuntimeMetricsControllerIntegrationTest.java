package gal.conxugal.application.rest.admin.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import gal.conxugal.application.http.auth.support.AuthenticationTestSupport;
import gal.conxugal.application.http.auth.support.TestUserFactory;
import gal.conxugal.domain.metrics.HttpRequestCounter;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
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

  @Inject
  HttpRequestCounter httpRequestCounter;

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
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/metrics")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());
  }

  @Test
  void security_denied_subscription_is_still_counted(RequestSpecification spec) {
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.normalUser());
    RuntimeMetrics.Http before = httpRequestCounter.snapshot();

    given(spec)
        .header(HttpHeaders.COOKIE, sessionCookie)
    .when()
        .get("/api/admin/metrics")
    .then()
        .statusCode(HttpStatus.FORBIDDEN.getCode());

    // The counting filter sits at the METRICS phase, ahead of SECURITY, so a request the
    // security filter turns away never reaches the route yet still unwinds back through it.
    // Reorder the two and this is the request that stops being counted.
    assertThat(httpRequestCounter.snapshot().requestCount()).isEqualTo(before.requestCount() + 1);
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
  void stream_is_typed_as_events_and_asks_intermediaries_not_to_buffer(RequestSpecification spec) {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    HttpResponse<ByteBuffer<?>> response = openStreamForItsHeaders(sessionCookie);

    assertThat(response).isNotNull();
    HttpHeaders headers = response.getHeaders();
    assertThat(headers.get(HttpHeaders.CONTENT_TYPE)).startsWith(MediaType.TEXT_EVENT_STREAM);
    // A cache or a proxy that buffers holds every sample back until the body ends, which for a
    // stream the instance never closes is never. These two headers are what tell it not to.
    assertThat(headers.get(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-cache");
    assertThat(headers.get("X-Accel-Buffering")).isEqualTo("no");
  }

  @Test
  void failed_sample_is_skipped_rather_than_ending_the_stream(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample())
        .thenThrow(new IllegalStateException("the pool gauge is unavailable"))
        .thenReturn(sample(2));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

    StreamCollector collector = openStream(sessionCookie);
    try {
      // A sample the source cannot assemble must cost the viewer that one tick and nothing more.
      // Ending the stream instead would drop the panel into reconnecting on the first hiccup.
      collector.awaitText(text -> text.contains("\"active\":2"), Duration.ofSeconds(3));
    } finally {
      collector.dispose();
    }
  }

  @Test
  void admin_receives_first_sample_without_waiting_for_the_interval(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

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
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

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
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

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
  void subscribing_to_the_stream_is_counted_like_any_other_request(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());
    // Taken after the login because AuthenticationTestSupport#loginAs issues one POST /login and
    // follows no redirect, leaving the subscription below as the only request this delta can see.
    // Give that helper a second round trip and the exact count here is what tells you.
    RuntimeMetrics.Http before = httpRequestCounter.snapshot();

    StreamCollector collector = openStream(sessionCookie);
    try {
      // Waiting for a frame is what orders the filter's run before the snapshot below; the
      // bound is generous because promptness is another test's subject, not this one's.
      collector.awaitText(text -> countDataFrames(text) >= 1, Duration.ofSeconds(2));
    } finally {
      collector.dispose();
    }

    RuntimeMetrics.Http after = httpRequestCounter.snapshot();
    assertThat(after.requestCount()).isEqualTo(before.requestCount() + 1);
    assertThat(after.errorCount()).isEqualTo(before.errorCount());
  }

  @Test
  void disconnecting_the_client_releases_the_subscription_and_its_timer(RequestSpecification spec)
      throws InterruptedException {
    when(runtimeMetricsSource.currentSample()).thenReturn(sample(1));
    String sessionCookie = seedUserAndLoginAs(spec, TestUserFactory.adminUser());

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
    return new StreamCollector(Flux.from(streamingClient.dataStream(subscription(sessionCookie))));
  }

  /**
   * The answer to a subscription, taken from the first chunk and then cancelled. REST-assured
   * reads a whole body before handing over a response, so it cannot ask a stream that never ends
   * what headers it opened with.
   */
  private HttpResponse<ByteBuffer<?>> openStreamForItsHeaders(String sessionCookie) {
    return Flux.from(streamingClient.exchangeStream(subscription(sessionCookie)))
        .blockFirst(Duration.ofSeconds(3));
  }

  private static HttpRequest<?> subscription(String sessionCookie) {
    return HttpRequest.GET("/api/admin/metrics").header(HttpHeaders.COOKIE, sessionCookie);
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
