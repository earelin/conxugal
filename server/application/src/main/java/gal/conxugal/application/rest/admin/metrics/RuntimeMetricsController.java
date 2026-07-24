package gal.conxugal.application.rest.admin.metrics;

import gal.conxugal.domain.metrics.RuntimeMetrics;
import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import reactor.core.publisher.Flux;

@Controller("/api/admin/metrics")
@Secured("ADMIN")
class RuntimeMetricsController {

  private static final String HEARTBEAT_COMMENT = "heartbeat";

  private final RuntimeMetricsSource runtimeMetricsSource;
  private final ObjectMapper objectMapper;
  private final MetricsStreamProperties metricsStreamProperties;

  RuntimeMetricsController(
      RuntimeMetricsSource runtimeMetricsSource,
      ObjectMapper objectMapper,
      MetricsStreamProperties metricsStreamProperties) {
    this.runtimeMetricsSource = runtimeMetricsSource;
    this.objectMapper = objectMapper;
    this.metricsStreamProperties = metricsStreamProperties;
  }

  @Get(produces = MediaType.TEXT_EVENT_STREAM)
  HttpResponse<Flux<Event<String>>> stream() {
    Flux<Event<String>> samples =
        Flux.interval(Duration.ZERO, metricsStreamProperties.sampleInterval())
            .onBackpressureLatest()
            .map(tick -> Event.of(toJson(runtimeMetricsSource.currentSample())));
    Flux<Event<String>> heartbeats =
        Flux.interval(
                metricsStreamProperties.heartbeatInterval(),
                metricsStreamProperties.heartbeatInterval())
            .onBackpressureLatest()
            .map(tick -> Event.of("").comment(HEARTBEAT_COMMENT));

    return HttpResponse.ok(Flux.merge(samples, heartbeats))
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header("X-Accel-Buffering", "no");
  }

  private String toJson(RuntimeMetrics sample) {
    try {
      return objectMapper.writeValueAsString(RuntimeMetricsResponse.of(sample));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
