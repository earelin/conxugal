package gal.conxugal.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RuntimeMetricsTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-07-18T09:30:00Z");
  private static final RuntimeMetrics.Jvm JVM =
      new RuntimeMetrics.Jvm(536870912L, 2147483648L, 100663296L, 42, 3600000L, 17L, 250L);
  private static final RuntimeMetrics.SystemLoad SYSTEM = new RuntimeMetrics.SystemLoad(0.35);
  private static final RuntimeMetrics.Http HTTP = new RuntimeMetrics.Http(15230L, 12L);
  private static final RuntimeMetrics.DatastorePool POOL =
      new RuntimeMetrics.DatastorePool(3, 7, 10);

  @Test
  void exposes_the_sample_it_was_built_from() {
    RuntimeMetrics metrics = new RuntimeMetrics(TIMESTAMP, JVM, SYSTEM, HTTP, POOL);

    assertThat(metrics.timestamp()).isEqualTo(TIMESTAMP);
    assertThat(metrics.jvm().heapUsedBytes()).isEqualTo(536870912L);
    assertThat(metrics.jvm().heapMaxBytes()).isEqualTo(2147483648L);
    assertThat(metrics.jvm().nonHeapUsedBytes()).isEqualTo(100663296L);
    assertThat(metrics.jvm().threadCount()).isEqualTo(42);
    assertThat(metrics.jvm().uptimeMillis()).isEqualTo(3600000L);
    assertThat(metrics.jvm().gcCollectionCount()).isEqualTo(17L);
    assertThat(metrics.jvm().gcCollectionTimeMillis()).isEqualTo(250L);
    assertThat(metrics.system().cpuLoad()).isEqualTo(0.35);
    assertThat(metrics.http().requestCount()).isEqualTo(15230L);
    assertThat(metrics.http().errorCount()).isEqualTo(12L);
    assertThat(metrics.datastorePool().active()).isEqualTo(3);
    assertThat(metrics.datastorePool().idle()).isEqualTo(7);
    assertThat(metrics.datastorePool().max()).isEqualTo(10);
  }

  @Test
  void samples_with_the_same_figures_are_equal() {
    assertThat(new RuntimeMetrics(TIMESTAMP, JVM, SYSTEM, HTTP, POOL))
        .isEqualTo(new RuntimeMetrics(TIMESTAMP, JVM, SYSTEM, HTTP, POOL));
  }

  @Test
  void rejects_null_timestamp() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RuntimeMetrics(null, JVM, SYSTEM, HTTP, POOL));
  }

  @Test
  void rejects_null_jvm_section() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RuntimeMetrics(TIMESTAMP, null, SYSTEM, HTTP, POOL));
  }

  @Test
  void rejects_null_system_section() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RuntimeMetrics(TIMESTAMP, JVM, null, HTTP, POOL));
  }

  @Test
  void rejects_null_http_section() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RuntimeMetrics(TIMESTAMP, JVM, SYSTEM, null, POOL));
  }

  @Test
  void rejects_null_datastore_pool_section() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RuntimeMetrics(TIMESTAMP, JVM, SYSTEM, HTTP, null));
  }

  @Test
  void rejects_negative_jvm_figure() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.Jvm(-1L, 2147483648L, 0L, 1, 0L, 0L, 0L))
        .withMessageContaining("heapUsedBytes");
  }

  @Test
  void rejects_negative_http_counter() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.Http(0L, -1L))
        .withMessageContaining("errorCount");
  }

  @Test
  void rejects_negative_pool_figure() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.DatastorePool(0, -1, 10))
        .withMessageContaining("idle");
  }

  @Test
  void accepts_figures_at_zero() {
    RuntimeMetrics metrics =
        new RuntimeMetrics(
            TIMESTAMP,
            new RuntimeMetrics.Jvm(0L, 0L, 0L, 0, 0L, 0L, 0L),
            SYSTEM,
            new RuntimeMetrics.Http(0L, 0L),
            new RuntimeMetrics.DatastorePool(0, 0, 0));

    assertThat(metrics.jvm().heapUsedBytes()).isZero();
    assertThat(metrics.http().requestCount()).isZero();
    assertThat(metrics.datastorePool().max()).isZero();
  }

  @Test
  void accepts_cpu_load_at_both_bounds() {
    assertThat(new RuntimeMetrics.SystemLoad(0.0).cpuLoad()).isEqualTo(0.0);
    assertThat(new RuntimeMetrics.SystemLoad(1.0).cpuLoad()).isEqualTo(1.0);
  }

  @Test
  void accepts_absent_cpu_load() {
    assertThat(new RuntimeMetrics.SystemLoad(null).cpuLoad()).isNull();
  }

  @Test
  void rejects_negative_cpu_load() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.SystemLoad(-0.01))
        .withMessageContaining("cpuLoad");
  }

  @Test
  void rejects_cpu_load_above_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.SystemLoad(1.01))
        .withMessageContaining("cpuLoad");
  }

  @Test
  void rejects_nan_cpu_load() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RuntimeMetrics.SystemLoad(Double.NaN))
        .withMessageContaining("cpuLoad");
  }

  @Test
  void carries_only_numeric_figures_so_secrets_are_unrepresentable() {
    assertThat(leafTypesOf(RuntimeMetrics.class))
        .isNotEmpty()
        .allSatisfy(
            type -> assertThat(type).isIn(Instant.class, long.class, int.class, Double.class));
  }

  private static Stream<Class<?>> leafTypesOf(Class<?> record) {
    return Arrays.stream(record.getRecordComponents())
        .map(RecordComponent::getType)
        .flatMap(type -> type.isRecord() ? leafTypesOf(type) : Stream.of(type));
  }
}
