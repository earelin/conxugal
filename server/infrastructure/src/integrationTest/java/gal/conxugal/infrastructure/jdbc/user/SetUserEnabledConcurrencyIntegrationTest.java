package gal.conxugal.infrastructure.jdbc.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.user.LastEnabledAdminException;
import gal.conxugal.domain.user.SetUserEnabled;
import gal.conxugal.domain.user.UserId;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves the {@link SetUserEnabled} last-admin guard is race-free against a real
 * Postgres: two concurrent disables of the last two enabled admins cannot both succeed.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SetUserEnabledConcurrencyIntegrationTest implements TestPropertyProvider {

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
  SetUserEnabled setUserEnabled;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE users");
    }
  }

  @Test
  void sequentially_disabling_both_of_the_last_two_enabled_admins_refuses_the_second()
      throws Exception {
    disableSeededDefaultAdmin();
    UserId firstAdminId = insertEnabledAdmin("admin-a@example.com");
    UserId secondAdminId = insertEnabledAdmin("admin-b@example.com");

    setUserEnabled.setEnabled(firstAdminId, false);

    assertThatThrownBy(() -> setUserEnabled.setEnabled(secondAdminId, false))
        .isInstanceOf(LastEnabledAdminException.class);
  }

  @Test
  void two_concurrent_disables_of_the_last_two_enabled_admins_cannot_both_succeed()
      throws Exception {
    disableSeededDefaultAdmin();
    UserId firstAdminId = insertEnabledAdmin("admin-a@example.com");
    UserId secondAdminId = insertEnabledAdmin("admin-b@example.com");

    CyclicBarrier barrier = new CyclicBarrier(2);
    List<Optional<Exception>> outcomes;
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Optional<Exception>>> futures = List.of(firstAdminId, secondAdminId).stream()
          .map(id -> executor.submit(disableAndCaptureFailure(id, barrier)))
          .toList();

      outcomes = new ArrayList<>();
      for (Future<Optional<Exception>> future : futures) {
        outcomes.add(future.get(10, TimeUnit.SECONDS));
      }
    }

    List<Exception> failures = outcomes.stream().filter(Optional::isPresent)
        .map(Optional::orElseThrow).toList();
    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(LastEnabledAdminException.class);
    assertThat(countEnabledAdmins()).isEqualTo(1L);
  }

  private long countEnabledAdmins() throws Exception {
    String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND enabled = TRUE";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      if (!resultSet.next()) {
        throw new IllegalStateException("COUNT(*) query returned no row");
      }
      return resultSet.getLong(1);
    }
  }

  // Reports setEnabled's outcome instead of letting it escape: this runs inside a pooled
  // Callable, so a failure needs to reach the assertions on the test thread as data, not as
  // an uncaught exception on a worker thread.
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private Callable<Optional<Exception>> disableAndCaptureFailure(
      UserId id, CyclicBarrier barrier) {
    return () -> {
      barrier.await();
      try {
        setUserEnabled.setEnabled(id, false);
        return Optional.empty();
      } catch (Exception e) {
        return Optional.of(e);
      }
    };
  }

  // V3__seed_default_admin_user.sql seeds an enabled "root@local" ADMIN on every fresh
  // schema; disabled here (via a committed raw-JDBC update, like insertEnabledAdmin below —
  // going through the injected UserRepository isn't guaranteed to commit before the
  // background threads below run on their own separately-borrowed connections) so the test
  // starts from exactly the two enabled admins it seeds.
  private void disableSeededDefaultAdmin() throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("UPDATE users SET enabled = false WHERE email = ?")) {
      statement.setString(1, "root@local");
      statement.executeUpdate();
      connection.commit();
    }
  }

  private UserId insertEnabledAdmin(String email) throws Exception {
    String sql = "INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, email);
      statement.setString(2, "hashed-password");
      statement.setString(3, "ADMIN");
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new SQLException("INSERT ... RETURNING id produced no row");
        }
        UserId id = new UserId((UUID) resultSet.getObject("id"));
        connection.commit();
        return id;
      }
    }
  }
}
