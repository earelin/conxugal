package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.organo.TermoRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.transaction.TransactionOperations;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@code lockTaxonomia} actually serialises, against a real Postgres and from two
 * transactions running concurrently on their own connections. A single-threaded test cannot
 * tell a held lock from one that was released the instant it was taken, which is exactly what
 * a transaction-scoped advisory lock degrades into when there is no ambient transaction — so
 * this is the only test in the suite that fails when the lock is a no-op.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaxonomiaLockConcurrencyIntegrationTest implements TestPropertyProvider {

  private static final long TIMEOUT_SECONDS = 10L;

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @Inject
  TermoRepository termoRepository;

  @Inject
  TransactionOperations<Connection> transactionOperations;

  @Test
  void second_transaction_waits_for_the_first_to_finish_before_taking_the_lock()
      throws Exception {
    CountDownLatch firstHoldsLock = new CountDownLatch(1);
    CountDownLatch firstMayCommit = new CountDownLatch(1);
    CountDownLatch secondTookLock = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      final Future<?> first = executor.submit(() -> transactionOperations.executeWrite(status -> {
        termoRepository.lockTaxonomia();
        firstHoldsLock.countDown();
        awaitQuietly(firstMayCommit);
        return null;
      }));
      assertThat(firstHoldsLock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

      final Future<?> second = executor.submit(() -> transactionOperations.executeWrite(status -> {
        termoRepository.lockTaxonomia();
        secondTookLock.countDown();
        return null;
      }));

      // Still blocked: the first transaction holds the lock and has not committed. A lock
      // taken outside a transaction would already have been released, letting this through.
      assertThat(secondTookLock.await(1L, TimeUnit.SECONDS)).isFalse();

      firstMayCommit.countDown();
      first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

      // Through, and with no unlock anywhere in the code: committing is what released it.
      assertThat(secondTookLock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }

  @Test
  void lock_is_released_when_the_holding_transaction_rolls_back() throws Exception {
    CountDownLatch firstHoldsLock = new CountDownLatch(1);
    CountDownLatch firstMayRollBack = new CountDownLatch(1);

    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      Future<?> rollingBack = executor.submit(() -> transactionOperations.executeWrite(status -> {
        termoRepository.lockTaxonomia();
        firstHoldsLock.countDown();
        awaitQuietly(firstMayRollBack);
        throw new IllegalStateException("rolling this transaction back");
      }));
      assertThat(firstHoldsLock.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

      Future<?> waiting = executor.submit(() -> transactionOperations.executeWrite(status -> {
        termoRepository.lockTaxonomia();
        return null;
      }));

      firstMayRollBack.countDown();
      assertThat(rollingBack).failsWithin(TIMEOUT_SECONDS, TimeUnit.SECONDS);

      // A rollback releases the lock just as a commit does — nothing has to unlock it.
      waiting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
  }

  // Runs inside a transaction callback that cannot declare a checked exception; restoring the
  // interrupt flag keeps a cancelled run from silently swallowing it.
  private static void awaitQuietly(CountDownLatch latch) {
    try {
      if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the test to release the lock");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while holding the taxonomy lock", e);
    }
  }
}
