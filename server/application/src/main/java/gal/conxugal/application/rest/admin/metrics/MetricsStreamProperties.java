package gal.conxugal.application.rest.admin.metrics;

import io.micronaut.context.annotation.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties("conxugal.metrics.stream")
record MetricsStreamProperties(Duration sampleInterval, Duration heartbeatInterval) {}
