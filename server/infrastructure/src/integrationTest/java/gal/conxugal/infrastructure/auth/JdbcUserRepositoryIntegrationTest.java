package gal.conxugal.infrastructure.auth;

import gal.conxugal.domain.auth.Role;
import gal.conxugal.domain.auth.User;
import gal.conxugal.domain.auth.UserRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

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
    void clean_up() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE users");
        }
    }

    @Test
    void finds_a_stored_user_by_email() throws Exception {
        insertUser("ana@example.com", "hashed-password", "ADMIN");

        Optional<User> result = userRepository.findByEmail("ana@example.com");

        assertThat(result).isPresent();
        User user = result.get();
        assertThat(user.id()).isNotNull();
        assertThat(user.email()).isEqualTo("ana@example.com");
        assertThat(user.passwordHash()).isEqualTo("hashed-password");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void returns_empty_for_an_unknown_email() {
        Optional<User> result = userRepository.findByEmail("ghost@example.com");

        assertThat(result).isEmpty();
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
