package gal.conxugal.application.rest.admin.metrics;

import gal.conxugal.domain.metrics.RuntimeMetricsSource;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.sse.Event;
import io.micronaut.security.annotation.Secured;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

@Controller("/api/admin/metrics")
@Secured("ADMIN")
class RuntimeMetricsController {

  private static final Logger LOG = LoggerFactory.getLogger(RuntimeMetricsController.class);
  private static final String HEARTBEAT_COMMENT = "heartbeat";

  private final RuntimeMetricsSource runtimeMetricsSource;
  private final MetricsStreamProperties metricsStreamProperties;

  RuntimeMetricsController(
      RuntimeMetricsSource runtimeMetricsSource,
      MetricsStreamProperties metricsStreamProperties) {
    this.runtimeMetricsSource = runtimeMetricsSource;
    this.metricsStreamProperties = metricsStreamProperties;
  }

  @Get(produces = MediaType.TEXT_EVENT_STREAM)
  HttpResponse<Flux<Event<Object>>> stream() {
    Flux<Event<Object>> samples =
        Flux.interval(Duration.ZERO, metricsStreamProperties.sampleInterval())
            .onBackpressureLatest()
            .map(tick ->
                Event.<Object>of(RuntimeMetricsResponse.of(runtimeMetricsSource.currentSample())))
            .onErrorContinue(
                (throwable, tick) -> LOG.warn("Skipping a runtime metrics sample", throwable));
    Flux<Event<Object>> heartbeats =
        Flux.interval(
                metricsStreamProperties.heartbeatInterval(),
                metricsStreamProperties.heartbeatInterval())
            .onBackpressureLatest()
            .map(tick -> Event.<Object>of("").comment(HEARTBEAT_COMMENT));

    return HttpResponse.ok(Flux.merge(samples, heartbeats))
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header("X-Accel-Buffering", "no");
  }
}
