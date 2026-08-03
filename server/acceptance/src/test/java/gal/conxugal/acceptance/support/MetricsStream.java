package gal.conxugal.acceptance.support;

import io.restassured.path.json.JsonPath;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * An open {@code text/event-stream} connection to the running instance's metrics endpoint.
 *
 * <p>REST-assured reads a whole response body before handing it over, so it cannot consume a
 * stream that never ends; the JDK's own client can. Frames are read off the connection as they
 * arrive and handed to the scenario verbatim, so it can assert on the exact bytes the instance
 * put on the wire as well as on the values parsed out of them.
 */
public final class MetricsStream implements Closeable {

  /** Where the stream lives and what it speaks, for scenarios that call it without subscribing. */
  public static final String PATH = "/api/admin/metrics";
  public static final String MEDIA_TYPE = "text/event-stream";

  private static final Logger LOG = System.getLogger(MetricsStream.class.getName());
  private static final String DATA_FIELD = "data:";

  private final InputStream body;
  private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

  private volatile boolean ended;

  private MetricsStream(InputStream body) {
    this.body = body;
  }

  /** Opens the stream as the holder of {@code sessionCookie}, the way an EventSource would. */
  public static MetricsStream openAs(String sessionCookie) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(ApplicationUnderTest.BASE_URI + PATH))
            .header("Accept", MEDIA_TYPE)
            .header("Cookie", sessionCookie)
            .GET()
            .build();
    HttpResponse<InputStream> response = send(request);
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "The metrics stream refused the connection with %d".formatted(response.statusCode()));
    }
    MetricsStream stream = new MetricsStream(response.body());
    stream.readInBackground();
    return stream;
  }

  /**
   * The next whole frame, or empty when the stream stays silent for {@code timeout} — a silence
   * an instance that hung up cannot produce, so that case is a failure rather than an absence.
   */
  public Optional<String> nextFrameWithin(Duration timeout) {
    Optional<String> frame = poll(timeout);
    if (frame.isEmpty() && ended) {
      throw new IllegalStateException("The instance closed the stream");
    }
    return frame;
  }

  /**
   * The next frame carrying a sample, verbatim so a scenario can assert on the bytes
   * themselves, skipping any heartbeat comment that arrives between two samples.
   */
  public String nextSampleFrame(Duration timeout) {
    return nextSampleFrameMatching(sample -> true, timeout);
  }

  /**
   * The next sample satisfying {@code condition}. Samples the instance assembled before the
   * scenario acted are already queued, so waiting for a state the scenario caused means
   * reading past them rather than trusting whichever frame happens to come next.
   */
  public String nextSampleFrameMatching(Predicate<JsonPath> condition, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    String frame = nextSample(timeout);
    while (!condition.test(valuesOf(frame))) {
      Duration left = Duration.between(Instant.now(), deadline);
      if (!left.isPositive()) {
        throw new NoSuchElementException(
            "No sample matched within %s; the last one read was %s".formatted(timeout, frame));
      }
      frame = nextSample(left);
    }
    return frame;
  }

  /** The values a sample frame carries. */
  public static JsonPath valuesOf(String sampleFrame) {
    return JsonPath.from(dataOf(sampleFrame));
  }

  @Override
  public void close() throws IOException {
    body.close();
  }

  private String nextFrame(Duration timeout) {
    return nextFrameWithin(timeout)
        .orElseThrow(() -> new NoSuchElementException("No frame within %s".formatted(timeout)));
  }

  private String nextSample(Duration timeout) {
    String frame = nextFrame(timeout);
    while (dataOf(frame).isBlank()) {
      frame = nextFrame(timeout);
    }
    return frame;
  }

  private Optional<String> poll(Duration timeout) {
    try {
      return Optional.ofNullable(frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting a frame", interrupted);
    }
  }

  private static String dataOf(String frame) {
    return frame.lines()
        .filter(line -> line.startsWith(DATA_FIELD))
        .map(line -> line.substring(DATA_FIELD.length()).trim())
        .reduce("", String::concat);
  }

  private static HttpResponse<InputStream> send(HttpRequest request) {
    try {
      return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
    } catch (IOException failed) {
      throw new UncheckedIOException(failed);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while opening the metrics stream", interrupted);
    }
  }

  private void readInBackground() {
    Thread reader = new Thread(this::readFrames, "metrics-stream-reader");
    reader.setDaemon(true);
    reader.start();
  }

  private void readFrames() {
    StringBuilder frame = new StringBuilder();
    try (BufferedReader lines =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line = lines.readLine();
      while (line != null) {
        if (!line.isEmpty()) {
          frame.append(line).append('\n');
        } else if (!frame.isEmpty()) {
          // A heartbeat carries no data line, so it reaches us as two blank lines in a row;
          // the second closes nothing and must not surface as a frame of its own.
          frames.add(frame.toString());
          frame.setLength(0);
        }
        line = lines.readLine();
      }
    } catch (IOException hungUp) {
      LOG.log(Level.DEBUG, "The metrics stream ended", hungUp);
    }
    ended = true;
  }
}
