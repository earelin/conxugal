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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An open {@code text/event-stream} connection to the running instance's metrics endpoint.
 *
 * <p>REST-assured reads a whole response body before handing it over, so it cannot consume a
 * stream that never ends; the JDK's own client can. Frames are read off the connection as they
 * arrive and handed to the scenario verbatim, so it can assert on the exact bytes the instance
 * put on the wire as well as on the values parsed out of them.
 */
public final class MetricsStream implements Closeable {

  private static final Logger LOG = System.getLogger(MetricsStream.class.getName());
  private static final String METRICS_PATH = "/api/admin/metrics";
  private static final String EVENT_STREAM = "text/event-stream";
  private static final String DATA_FIELD = "data:";
  private static final String NO_MORE_FRAMES = "";

  private final InputStream body;
  private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

  private MetricsStream(InputStream body) {
    this.body = body;
  }

  /** Opens the stream as the holder of {@code sessionCookie}, the way an EventSource would. */
  public static MetricsStream openAs(String sessionCookie) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(ApplicationUnderTest.BASE_URI + METRICS_PATH))
            .header("Accept", EVENT_STREAM)
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

  /** The next whole frame, comments included, or a failure when none arrives in time. */
  public String nextFrame(Duration timeout) {
    return nextFrameWithin(timeout)
        .orElseThrow(() -> new NoSuchElementException("No frame within %s".formatted(timeout)));
  }

  /** The next whole frame, or empty when the stream stays silent for {@code timeout}. */
  public Optional<String> nextFrameWithin(Duration timeout) {
    try {
      return Optional.ofNullable(frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting a frame", interrupted);
    }
  }

  /**
   * The next frame carrying a sample, verbatim so a scenario can assert on the bytes
   * themselves, skipping any heartbeat comment that arrives between two samples.
   */
  public String nextSampleFrame(Duration timeout) {
    String frame = nextFrame(timeout);
    while (dataOf(frame).isBlank()) {
      frame = nextFrame(timeout);
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
        if (line.isEmpty()) {
          frames.add(frame.toString());
          frame.setLength(0);
        } else {
          frame.append(line).append('\n');
        }
        line = lines.readLine();
      }
    } catch (IOException ended) {
      LOG.log(Level.DEBUG, "The metrics stream ended", ended);
    }
    frames.add(NO_MORE_FRAMES);
  }
}
