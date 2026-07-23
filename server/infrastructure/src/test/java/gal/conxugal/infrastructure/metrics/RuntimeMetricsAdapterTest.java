package gal.conxugal.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariPoolMXBean;
import gal.conxugal.domain.metrics.HttpRequestCounter;
import gal.conxugal.domain.metrics.RuntimeMetrics;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import javax.management.ObjectName;
import org.junit.jupiter.api.Test;

class RuntimeMetricsAdapterTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-07-18T09:30:00Z");

  private final HttpRequestCounter httpRequestCounter = new HttpRequestCounter();
  private final FakeHikariPool pool = new FakeHikariPool(3, 7);
  private final RuntimeMetricsAdapter adapter =
      new RuntimeMetricsAdapter(() -> FIXED_INSTANT, httpRequestCounter, pool, 10);

  @Test
  void stamps_the_sample_with_the_injected_clock() {
    RuntimeMetrics metrics = adapter.currentSample();

    assertThat(metrics.timestamp()).isEqualTo(FIXED_INSTANT);
  }

  @Test
  void reads_plausible_jvm_figures_from_the_running_platform() {
    RuntimeMetrics.Jvm jvm = adapter.currentSample().jvm();

    assertThat(jvm.heapUsedBytes()).isPositive();
    assertThat(jvm.heapMaxBytes()).isPositive();
    assertThat(jvm.threadCount()).isPositive();
    assertThat(jvm.uptimeMillis()).isPositive();
    assertThat(jvm.gcCollectionCount()).isNotNegative();
    assertThat(jvm.gcCollectionTimeMillis()).isNotNegative();
  }

  @Test
  void reports_fresh_thread_count_rather_than_cached_one() throws InterruptedException {
    int before = adapter.currentSample().jvm().threadCount();
    CountDownLatch started = new CountDownLatch(1);
    Thread parked =
        new Thread(
            () -> {
              started.countDown();
              park();
            });
    parked.start();
    try {
      started.await();
      int after = adapter.currentSample().jvm().threadCount();
      assertThat(after).isGreaterThan(before);
    } finally {
      parked.interrupt();
      parked.join();
    }
  }

  @Test
  void maps_the_http_counter_snapshot_into_the_sample() {
    httpRequestCounter.recordRequest();
    httpRequestCounter.recordRequest();
    httpRequestCounter.recordError();

    RuntimeMetrics.Http http = adapter.currentSample().http();

    assertThat(http.requestCount()).isEqualTo(2L);
    assertThat(http.errorCount()).isEqualTo(1L);
  }

  @Test
  void reads_pool_usage_from_the_pool_gauges_and_the_configured_max_size() {
    RuntimeMetrics.DatastorePool datastorePool = adapter.currentSample().datastorePool();

    assertThat(datastorePool.active()).isEqualTo(3);
    assertThat(datastorePool.idle()).isEqualTo(7);
    assertThat(datastorePool.max()).isEqualTo(10);
  }

  @Test
  void reflects_pool_gauge_change_on_the_next_call() {
    assertThat(adapter.currentSample().datastorePool().active()).isEqualTo(3);

    pool.active = 5;

    assertThat(adapter.currentSample().datastorePool().active()).isEqualTo(5);
  }

  @Test
  void reports_absent_cpu_load_when_the_platform_mxbean_does_not_expose_it() {
    OperatingSystemMXBean plainOperatingSystem = new PlainOperatingSystem();

    RuntimeMetrics.SystemLoad systemLoad =
        RuntimeMetricsAdapter.systemLoadFrom(plainOperatingSystem);

    assertThat(systemLoad.cpuLoad()).isNull();
  }

  @Test
  void reads_bounded_cpu_load_when_the_platform_mxbean_reports_one() {
    RuntimeMetrics.SystemLoad systemLoad =
        RuntimeMetricsAdapter.systemLoadFrom(ManagementFactory.getOperatingSystemMXBean());

    assertThat(systemLoad.cpuLoad()).satisfiesAnyOf(
        cpuLoad -> assertThat(cpuLoad).isNull(),
        cpuLoad -> assertThat(cpuLoad).isBetween(0.0, 1.0));
  }

  private static void park() {
    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final class FakeHikariPool implements HikariPoolMXBean {

    private int active;
    private final int idle;

    private FakeHikariPool(int active, int idle) {
      this.active = active;
      this.idle = idle;
    }

    @Override
    public int getActiveConnections() {
      return active;
    }

    @Override
    public int getIdleConnections() {
      return idle;
    }

    @Override
    public int getTotalConnections() {
      return active + idle;
    }

    @Override
    public int getThreadsAwaitingConnection() {
      return 0;
    }

    @Override
    public void softEvictConnections() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void suspendPool() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void resumePool() {
      throw new UnsupportedOperationException();
    }
  }

  private static final class PlainOperatingSystem implements OperatingSystemMXBean {

    @Override
    public String getName() {
      return "test";
    }

    @Override
    public String getArch() {
      return "test";
    }

    @Override
    public String getVersion() {
      return "test";
    }

    @Override
    public int getAvailableProcessors() {
      return 1;
    }

    @Override
    public double getSystemLoadAverage() {
      return -1.0;
    }

    @Override
    public ObjectName getObjectName() {
      return null;
    }
  }
}
