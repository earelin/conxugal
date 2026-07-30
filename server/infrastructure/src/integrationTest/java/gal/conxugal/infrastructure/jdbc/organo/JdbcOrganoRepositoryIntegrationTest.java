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
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void finds_all_stored_organos_with_name_and_active_state() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", true);
    insertOrgano("axencia-y", "Axencia Y", false);

    List<OrganoDeContratacion> organos = organoRepository.findAllOrderByName();

    assertThat(organos)
        .extracting(
            OrganoDeContratacion::sourceKey,
            OrganoDeContratacion::name,
            OrganoDeContratacion::active)
        .containsExactly(
            tuple("axencia-y", "Axencia Y", false),
            tuple("consorcio-x", "Consorcio X", true));
  }

  @Test
  void orders_accented_names_under_the_galician_collation() throws Exception {
    insertOrgano("zamora", "Zamora", true);
    insertOrgano("avila", "Ávila", true);
    insertOrgano("avion", "Avión", true);

    List<OrganoDeContratacion> organos = organoRepository.findAllOrderByName();

    // Under the cluster default this returns Avión, Zamora, Ávila — the accent sorting
    // after Z is exactly what the column's collation exists to prevent.
    assertThat(organos)
        .extracting(OrganoDeContratacion::name)
        .containsExactly("Ávila", "Avión", "Zamora");
  }

  @Test
  void finds_organos_matching_given_source_keys() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", true);
    insertOrgano("axencia-y", "Axencia Y", true);
    insertOrgano("concello-z", "Concello Z", true);

    List<OrganoDeContratacion> organos =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x", "concello-z"));

    assertThat(organos)
        .extracting(OrganoDeContratacion::sourceKey)
        .containsExactlyInAnyOrder("consorcio-x", "concello-z");
  }

  @Test
  void inserts_an_organo_with_database_generated_id() {
    OrganoDeContratacion newOrgano = new OrganoDeContratacion("consorcio-x", "Consorcio X");

    OrganoDeContratacion created = organoRepository.insert(newOrgano);

    assertThat(created.id()).isNotNull();
    assertThat(organoRepository.findAllOrderByName())
        .extracting(OrganoDeContratacion::sourceKey, OrganoDeContratacion::active)
        .containsExactly(tuple("consorcio-x", true));
  }

  @Test
  void rejects_inserting_duplicate_source_key_without_altering_the_existing_row()
      throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", true);
    // The injected DataSource is Micronaut Data's connection-context-aware proxy, so
    // dataSource.getConnection() here shares the same underlying connection as the
    // repository call below, not a fresh one from the pool. Committing now is what lets
    // this row survive the rollback the aborted duplicate insert forces on that shared
    // connection.
    try (Connection connection = dataSource.getConnection()) {
      connection.commit();
    }
    OrganoDeContratacion duplicate = new OrganoDeContratacion("consorcio-x", "Other Name");

    assertThatThrownBy(() -> organoRepository.insert(duplicate))
        .isInstanceOf(RuntimeException.class);
    // Postgres refuses further commands on that connection until the aborted transaction
    // is rolled back; AssertJ DB below reuses the same shared connection, so it would
    // otherwise fail with "current transaction is aborted".
    try (Connection rollbackConnection = dataSource.getConnection()) {
      rollbackConnection.rollback();
    }

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    Table organos = assertDbConnection.table("organo_contratacion").build();
    assertThat(organos).hasNumberOfRows(1);
    assertThat(organos).row(0).value("name").isEqualTo("Consorcio X");
  }

  @Test
  void updates_name_and_active_on_the_existing_row_matched_by_id() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true);

    organoRepository.update(id, "Consorcio X Renamed", false);

    OrganoDeContratacion updated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(updated.id()).isEqualTo(id);
    assertThat(updated.sourceKey()).isEqualTo("consorcio-x");
    assertThat(updated.name()).isEqualTo("Consorcio X Renamed");
    assertThat(updated.active()).isFalse();
  }

  @Test
  void update_preserves_other_stored_rows() throws Exception {
    insertOrgano("consorcio-x", "Consorcio X", true);
    UUID otherId = insertOrgano("axencia-y", "Axencia Y", true);

    organoRepository.update(otherId, "Axencia Y Renamed", true);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(untouched.name()).isEqualTo("Consorcio X");
  }

  @Test
  void toggles_the_active_state_without_touching_name() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true);

    organoRepository.updateActive(id, false);

    OrganoDeContratacion deactivated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(deactivated.active()).isFalse();
    assertThat(deactivated.name()).isEqualTo("Consorcio X");

    organoRepository.updateActive(id, true);

    OrganoDeContratacion reactivated =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(reactivated.active()).isTrue();
  }

  @Test
  void set_active_only_changes_the_matched_row() throws Exception {
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true);
    insertOrgano("axencia-y", "Axencia Y", true);

    organoRepository.updateActive(id, false);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("axencia-y")).get(0);
    assertThat(untouched.active()).isTrue();
  }

  @Test
  void update_preserves_existing_placement() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true, termoId);

    organoRepository.update(id, "Consorcio X Renamed", true);

    OrganoDeContratacion updated = organoRepository.findById(id).orElseThrow();
    assertThat(updated.termoId()).isEqualTo(termoId);
  }

  @Test
  void updateActive_preserves_existing_placement() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true, termoId);

    organoRepository.updateActive(id, false);

    OrganoDeContratacion updated = organoRepository.findById(id).orElseThrow();
    assertThat(updated.termoId()).isEqualTo(termoId);
  }

  @Test
  void findAllOrderByName_reports_termo_id_for_placed_and_unplaced_organos() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    insertOrgano("consorcio-x", "Consorcio X", true, termoId);
    insertOrgano("axencia-y", "Axencia Y", true, null);

    List<OrganoDeContratacion> organos = organoRepository.findAllOrderByName();

    assertThat(organos)
        .extracting(OrganoDeContratacion::sourceKey, OrganoDeContratacion::termoId)
        .containsExactly(
            tuple("axencia-y", null),
            tuple("consorcio-x", termoId));
  }

  @Test
  void inserted_organo_is_unclassified_by_default() {
    OrganoDeContratacion created =
        organoRepository.insert(new OrganoDeContratacion("consorcio-x", "Consorcio X"));

    assertThat(organoRepository.findById(created.id()).orElseThrow().termoId()).isNull();
  }

  @Test
  void updateTermo_sets_then_replaces_then_clears_the_placement() throws Exception {
    UUID firstTermo = insertTermo("Deportes", null);
    UUID secondTermo = insertTermo("Cultura", null);
    UUID id = insertOrgano("consorcio-x", "Consorcio X", true);

    organoRepository.updateTermo(id, firstTermo);
    assertThat(organoRepository.findById(id).orElseThrow().termoId()).isEqualTo(firstTermo);

    organoRepository.updateTermo(id, secondTermo);
    assertThat(organoRepository.findById(id).orElseThrow().termoId()).isEqualTo(secondTermo);

    organoRepository.updateTermo(id, null);
    assertThat(organoRepository.findById(id).orElseThrow().termoId()).isNull();
  }

  @Test
  void clearPlacementsByTermo_clears_only_rows_placed_in_that_term() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    UUID otherTermoId = insertTermo("Cultura", null);
    UUID placedId = insertOrgano("consorcio-x", "Consorcio X", true, termoId);
    UUID placedElsewhereId = insertOrgano("axencia-y", "Axencia Y", true, otherTermoId);

    organoRepository.clearPlacementsByTermo(termoId);

    assertThat(organoRepository.findById(placedId).orElseThrow().termoId()).isNull();
    assertThat(organoRepository.findById(placedElsewhereId).orElseThrow().termoId())
        .isEqualTo(otherTermoId);
  }

  // Neither helper below commits or rolls back: every test here only expects successful
  // inserts, and the injected DataSource's shared connection lets the repository calls
  // under test see these uncommitted writes within the same transaction. Contrast
  // TermoMigrationIntegrationTest's insertTermo, which does commit/rollback because
  // several of its tests deliberately trigger a constraint violation.
  private UUID insertOrgano(String sourceKey, String name, boolean active) throws Exception {
    return insertOrgano(sourceKey, name, active, null);
  }

  private UUID insertOrgano(String sourceKey, String name, boolean active, UUID termoId)
      throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, termo_id) "
            + "VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setBoolean(3, active);
      statement.setObject(4, termoId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  private UUID insertTermo(String name, UUID parentId) throws Exception {
    String sql =
        "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setObject(2, parentId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }
}
