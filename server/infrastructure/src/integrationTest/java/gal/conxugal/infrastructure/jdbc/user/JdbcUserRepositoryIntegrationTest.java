package gal.conxugal.infrastructure.jdbc.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.auth.Authenticate;
import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.auth.UserRepository;
import gal.conxugal.infrastructure.crypto.Argon2idPasswordEncoder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcUserRepositoryIntegrationTest implements TestPropertyProvider {

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
  UserRepository userRepository;

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
  void finds_stored_user_by_email() throws Exception {
    insertUser("ana@example.com", "hashed-password", "ADMIN");

    Optional<User> result = userRepository.findByEmail("ana@example.com");

    assertThat(result).isPresent();
    User user = result.get();
    assertThat(user.id()).isNotNull();
    assertThat(user.email()).isEqualTo("ana@example.com");
    assertThat(user.passwordHash()).isEqualTo("hashed-password");
    assertThat(user.role()).isEqualTo(Role.ADMIN);
    assertThat(user.enabled()).isTrue();
    assertThat(user.createdAt()).isNotNull();
  }

  @Test
  void defaults_enabled_and_created_at_when_not_supplied() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");

    User user = userRepository.findByEmail("ana@example.com").orElseThrow();

    assertThat(user.enabled()).isTrue();
    assertThat(user.createdAt()).isNotNull();
  }

  @Test
  void finds_all_accounts_enabled_and_disabled() throws Exception {
    insertUser("ana@example.com", "hashed-password", "ADMIN");
    insertUser("iago@example.com", "hashed-password", "USER");
    UUID iagoId = userRepository.findByEmail("iago@example.com").orElseThrow().id();
    userRepository.updateEnabled(iagoId, false);

    List<User> users = userRepository.findAll();

    assertThat(users).hasSize(2);
    assertThat(users)
        .extracting(User::email, User::role, User::enabled)
        .containsExactlyInAnyOrder(
            tuple("ana@example.com", Role.ADMIN, true),
            tuple("iago@example.com", Role.USER, false));
  }

  @Test
  void creates_an_account_persisting_the_id_and_created_at_supplied_by_the_domain() {
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-01-15T09:30:00Z");
    User newUser = new User(id, "nova@example.com", "hashed-password", Role.USER, true, createdAt);

    User created = userRepository.create(newUser);

    assertThat(created.id()).isEqualTo(id);
    User found = userRepository.findByEmail("nova@example.com").orElseThrow();
    assertThat(found.id()).isEqualTo(id);
    assertThat(found.passwordHash()).isEqualTo("hashed-password");
    assertThat(found.role()).isEqualTo(Role.USER);
    assertThat(found.enabled()).isTrue();
    assertThat(found.createdAt()).isEqualTo(createdAt);
  }

  @Test
  void rejects_creating_duplicate_email() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");
    // @MicronautTest wraps the whole test method in one shared, rolled-back-at-the-end
    // transaction; committing here is what lets the row (and the row-unchanged assertion
    // below) survive the rollback this test triggers on purpose further down.
    try (Connection connection = dataSource.getConnection()) {
      connection.commit();
    }
    User duplicate =
        new User(UUID.randomUUID(), "ana@example.com", "other-hash", Role.ADMIN, true,
            Instant.parse("2026-01-15T09:30:00Z"));

    assertThatThrownBy(() -> userRepository.create(duplicate)).isInstanceOf(RuntimeException.class);
    // The failed insert leaves the pooled connection mid-transaction; Postgres refuses
    // further commands on it until it is rolled back, and the small test pool means the
    // next borrowed connection is likely that same one.
    try (Connection rollbackConnection = dataSource.getConnection()) {
      rollbackConnection.rollback();
    }

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    Table users = assertDbConnection.table("users").build();
    assertThat(users).hasNumberOfRows(1);
    assertThat(users).row(0)
        .value("password_hash").isEqualTo("hashed-password")
        .value("role").isEqualTo("USER");
  }

  @Test
  void toggles_the_enabled_state() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");
    User user = userRepository.findByEmail("ana@example.com").orElseThrow();

    userRepository.updateEnabled(user.id(), false);
    assertThat(userRepository.findByEmail("ana@example.com").orElseThrow().enabled()).isFalse();

    userRepository.updateEnabled(user.id(), true);
    assertThat(userRepository.findByEmail("ana@example.com").orElseThrow().enabled()).isTrue();
  }

  @Test
  void denies_authentication_for_disabled_account_and_allows_it_once_re_enabled()
      throws Exception {
    Argon2idPasswordEncoder passwordEncoder = new Argon2idPasswordEncoder();
    String rawPassword = "correct horse battery staple";
    insertUser("ana@example.com", passwordEncoder.encode(rawPassword), "USER");
    UUID id = userRepository.findByEmail("ana@example.com").orElseThrow().id();
    Instant fixedInstant = Instant.parse("2026-07-20T00:00:00Z");
    Authenticate authenticate =
        new Authenticate(userRepository, passwordEncoder, () -> fixedInstant);

    userRepository.updateEnabled(id, false);
    assertThat(authenticate.authenticate("ana@example.com", rawPassword)).isEmpty();

    userRepository.updateEnabled(id, true);
    assertThat(authenticate.authenticate("ana@example.com", rawPassword)).isPresent();
  }

  @Test
  void returns_empty_for_an_unknown_email() {
    Optional<User> result = userRepository.findByEmail("ghost@example.com");

    assertThat(result).isEmpty();
  }

  @Test
  void has_no_last_login_at_until_the_first_successful_login() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");

    User user = userRepository.findByEmail("ana@example.com").orElseThrow();

    assertThat(user.lastLoginAt()).isNull();
  }

  @Test
  void updates_last_login_at_for_an_existing_user() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");
    User user = userRepository.findByEmail("ana@example.com").orElseThrow();
    Instant loginInstant = Instant.parse("2026-07-11T10:15:30Z");

    userRepository.updateLastLoginAt(user.id(), loginInstant);

    User updated = userRepository.findByEmail("ana@example.com").orElseThrow();
    assertThat(updated.lastLoginAt()).isEqualTo(loginInstant);
  }

  @Test
  void replaces_last_login_at_on_the_next_login() throws Exception {
    insertUser("ana@example.com", "hashed-password", "USER");
    User user = userRepository.findByEmail("ana@example.com").orElseThrow();
    Instant firstLogin = Instant.parse("2026-07-11T10:15:30Z");
    Instant laterLogin = Instant.parse("2026-07-12T08:00:00Z");
    userRepository.updateLastLoginAt(user.id(), firstLogin);

    userRepository.updateLastLoginAt(user.id(), laterLogin);

    User updated = userRepository.findByEmail("ana@example.com").orElseThrow();
    assertThat(updated.lastLoginAt()).isEqualTo(laterLogin);
  }

  @Test
  void never_stores_the_password_as_plaintext() throws Exception {
    Argon2idPasswordEncoder passwordEncoder = new Argon2idPasswordEncoder();
    String rawPassword = "correct horse battery staple";
    insertUser("ana@example.com", passwordEncoder.encode(rawPassword), "USER");

    AssertDbConnection connection = AssertDbConnectionFactory.of(dataSource).create();
    Table users = connection.table("users").build();

    assertThat(users).row(0).value("password_hash").isNotEqualTo(rawPassword);
  }

  private void insertUser(String email, String passwordHash, String role) throws Exception {
    String sql = "INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, email);
      statement.setString(2, passwordHash);
      statement.setString(3, role);
      statement.executeUpdate();
    }
  }
}
