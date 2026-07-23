package gal.conxugal.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class HttpRequestCounterTest {

  @Test
  void starts_at_zero() {
    HttpRequestCounter counter = new HttpRequestCounter();

    RuntimeMetrics.Http snapshot = counter.snapshot();

    assertThat(snapshot.requestCount()).isZero();
    assertThat(snapshot.errorCount()).isZero();
  }

  @Test
  void recording_requests_moves_only_the_request_total() {
    HttpRequestCounter counter = new HttpRequestCounter();

    counter.recordRequest();
    counter.recordRequest();

    RuntimeMetrics.Http snapshot = counter.snapshot();
    assertThat(snapshot.requestCount()).isEqualTo(2L);
    assertThat(snapshot.errorCount()).isZero();
  }

  @Test
  void recording_an_error_moves_only_the_error_total() {
    HttpRequestCounter counter = new HttpRequestCounter();

    counter.recordError();

    RuntimeMetrics.Http snapshot = counter.snapshot();
    assertThat(snapshot.requestCount()).isZero();
    assertThat(snapshot.errorCount()).isEqualTo(1L);
  }

  @Test
  void concurrent_increments_are_not_lost() throws InterruptedException {
    int threadCount = 16;
    int incrementsPerThread = 1_000;
    HttpRequestCounter counter = new HttpRequestCounter();
    CountDownLatch startLine = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(threadCount);

    boolean completedInTime;
    try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        executor.execute(
            () -> {
              await(startLine);
              for (int j = 0; j < incrementsPerThread; j++) {
                counter.recordRequest();
                counter.recordError();
              }
              finished.countDown();
            });
      }
      startLine.countDown();
      completedInTime = finished.await(10, TimeUnit.SECONDS);
    }

    assertThat(completedInTime).isTrue();
    RuntimeMetrics.Http snapshot = counter.snapshot();
    assertThat(snapshot.requestCount()).isEqualTo((long) threadCount * incrementsPerThread);
    assertThat(snapshot.errorCount()).isEqualTo((long) threadCount * incrementsPerThread);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
