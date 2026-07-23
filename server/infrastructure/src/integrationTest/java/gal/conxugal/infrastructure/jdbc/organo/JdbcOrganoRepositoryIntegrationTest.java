package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
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
class JdbcOrganoRepositoryIntegrationTest implements TestPropertyProvider {

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
  OrganoRepository organoRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion");
    }
  }

  @Test
  void finds_all_stored_organos_with_name_acronym_and_active_state() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", "CX", true);
    insertOrgano("axencia-y", "Axencia Y", null, false);

    List<OrganoDeContratacion> organos = organoRepository.findAll();

    assertThat(organos)
        .extracting(
            OrganoDeContratacion::sourceKey,
            OrganoDeContratacion::name,
            OrganoDeContratacion::acronym,
            OrganoDeContratacion::active)
        .containsExactlyInAnyOrder(
            tuple("consorcio-x", "Consorcio X", "CX", true),
            tuple("axencia-y", "Axencia Y", null, false));
  }

  @Test
  void finds_organos_matching_given_source_keys() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", "CX", true);
    insertOrgano("axencia-y", "Axencia Y", null, true);
    insertOrgano("concello-z", "Concello Z", "CZ", true);

    List<OrganoDeContratacion> organos =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x", "concello-z"));

    assertThat(organos)
        .extracting(OrganoDeContratacion::sourceKey)
        .containsExactlyInAnyOrder("consorcio-x", "concello-z");
  }

  @Test
  void inserts_an_organo_with_database_generated_id() {
    OrganoDeContratacion newOrgano =
        new OrganoDeContratacion("consorcio-x", "Consorcio X", "CX");

    OrganoDeContratacion created = organoRepository.insert(newOrgano);

    assertThat(created.id()).isNotNull();
    assertThat(organoRepository.findAll())
        .extracting(OrganoDeContratacion::sourceKey, OrganoDeContratacion::active)
        .containsExactly(tuple("consorcio-x", true));
  }

  @Test
  void rejects_inserting_duplicate_source_key_without_altering_the_existing_row()
      throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", "CX", true);
    // @MicronautTest wraps the whole test method in one shared, rolled-back-at-the-end
    // transaction; committing here is what lets the row (and the row-unchanged assertion
    // below) survive the rollback this test triggers on purpose further down.
    try (Connection connection = dataSource.getConnection()) {
      connection.commit();
    }
    OrganoDeContratacion duplicate =
        new OrganoDeContratacion("consorcio-x", "Other Name", "OTH");

    assertThatThrownBy(() -> organoRepository.insert(duplicate))
        .isInstanceOf(RuntimeException.class);
    // The failed insert leaves the pooled connection mid-transaction; Postgres refuses
    // further commands on it until it is rolled back, and the small test pool means the
    // next borrowed connection is likely that same one.
    try (Connection rollbackConnection = dataSource.getConnection()) {
      rollbackConnection.rollback();
    }

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    Table organos = assertDbConnection.table("organo_contratacion").build();
    assertThat(organos).hasNumberOfRows(1);
    assertThat(organos).row(0)
        .value("name").isEqualTo("Consorcio X")
        .value("acronym").isEqualTo("CX");
  }

  @Test
  void updates_name_acronym_and_active_on_the_existing_row_matched_by_id() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", "CX", true);

    organoRepository.update(id, "Consorcio X Renamed", "CXR", false);

    OrganoDeContratacion updated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(updated.id()).isEqualTo(id);
    assertThat(updated.sourceKey()).isEqualTo("consorcio-x");
    assertThat(updated.name()).isEqualTo("Consorcio X Renamed");
    assertThat(updated.acronym()).isEqualTo("CXR");
    assertThat(updated.active()).isFalse();
  }

  @Test
  void update_preserves_other_stored_rows() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", "CX", true);
    UUID otherId = insertOrgano("axencia-y", "Axencia Y", null, true);

    organoRepository.update(otherId, "Axencia Y Renamed", "AY", true);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(untouched.name()).isEqualTo("Consorcio X");
    assertThat(untouched.acronym()).isEqualTo("CX");
  }

  @Test
  void update_clears_previously_set_acronym() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", "CX", true);

    organoRepository.update(id, "Consorcio X", null, true);

    OrganoDeContratacion updated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(updated.acronym()).isNull();
  }

  @Test
  void toggles_the_active_state_without_touching_name_or_acronym() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", "CX", true);

    organoRepository.setActive(id, false);

    OrganoDeContratacion deactivated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(deactivated.active()).isFalse();
    assertThat(deactivated.name()).isEqualTo("Consorcio X");
    assertThat(deactivated.acronym()).isEqualTo("CX");

    organoRepository.setActive(id, true);

    OrganoDeContratacion reactivated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(reactivated.active()).isTrue();
  }

  @Test
  void set_active_only_changes_the_matched_row() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", "CX", true);
    insertOrgano("axencia-y", "Axencia Y", null, true);

    organoRepository.setActive(id, false);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("axencia-y")).get(0);
    assertThat(untouched.active()).isTrue();
  }

  private UUID insertOrgano(String sourceKey, String name, String acronym, boolean active)
      throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, acronym, active) "
            + "VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setString(3, acronym);
      statement.setBoolean(4, active);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }
}
