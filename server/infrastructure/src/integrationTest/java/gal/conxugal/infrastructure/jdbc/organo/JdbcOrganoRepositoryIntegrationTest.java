package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.organo.taxonomia.TermoId;
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
import org.assertj.core.groups.Tuple;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
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
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true);

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
    OrganoId otherId = insertOrgano("axencia-y", "Axencia Y", true);

    organoRepository.update(otherId, "Axencia Y Renamed", true);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("consorcio-x")).get(0);
    assertThat(untouched.name()).isEqualTo("Consorcio X");
  }

  @Test
  void toggles_the_active_state_without_touching_name() throws Exception {
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true);

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
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true);
    insertOrgano("axencia-y", "Axencia Y", true);

    organoRepository.updateActive(id, false);

    OrganoDeContratacion untouched =
        organoRepository.findAllBySourceKeyIn(List.of("axencia-y")).get(0);
    assertThat(untouched.active()).isTrue();
  }

  @Test
  void update_preserves_existing_placement() throws Exception {
    TermoId termoId = insertTermo("Deportes", null);
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true, termoId);

    organoRepository.update(id, "Consorcio X Renamed", true);

    OrganoDeContratacion updated = organoRepository.findById(id).orElseThrow();
    assertThat(updated.termoId()).isEqualTo(termoId);
  }

  @Test
  void updateActive_preserves_existing_placement() throws Exception {
    TermoId termoId = insertTermo("Deportes", null);
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true, termoId);

    organoRepository.updateActive(id, false);

    OrganoDeContratacion updated = organoRepository.findById(id).orElseThrow();
    assertThat(updated.termoId()).isEqualTo(termoId);
  }

  @Test
  void findAllOrderByName_reports_termo_id_for_placed_and_unplaced_organos() throws Exception {
    TermoId termoId = insertTermo("Deportes", null);
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

  // Each stage is observed through findAllOrderByName rather than findById, because that is
  // the read GET /api/organos serves: a placement that only findById could see would leave
  // the catalogue showing an Órgano in the wrong term. The second Órgano is here so a
  // statement that updated more rows than it named cannot pass.
  @Test
  void updateTermo_sets_then_replaces_then_clears_the_placement() throws Exception {
    TermoId firstTermo = insertTermo("Deportes", null);
    TermoId secondTermo = insertTermo("Cultura", null);
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true);
    insertOrgano("axencia-y", "Axencia Y", true, secondTermo);

    organoRepository.updateTermo(id, firstTermo);
    assertThat(placementsByName()).containsExactly(
        tuple("Axencia Y", secondTermo), tuple("Consorcio X", firstTermo));

    organoRepository.updateTermo(id, secondTermo);
    assertThat(placementsByName()).containsExactly(
        tuple("Axencia Y", secondTermo), tuple("Consorcio X", secondTermo));

    organoRepository.updateTermo(id, null);
    assertThat(placementsByName()).containsExactly(
        tuple("Axencia Y", secondTermo), tuple("Consorcio X", null));
  }

  @Test
  void inserted_organo_is_unmarked_by_default() {
    organoRepository.insert(new OrganoDeContratacion("consorcio-x", "Consorcio X"));

    assertCatalogue(row("Consorcio X", true, false, null));
  }

  // The eligibility read filters in SQL, so it would still pass if the column never reached
  // the record. This is the read the administrator's catalogue and the importer both build on.
  @Test
  void findById_reports_the_mark_of_marked_organo() throws Exception {
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true, true, null);

    assertThat(organoRepository.findById(id).orElseThrow().importable()).isTrue();
  }

  // Asserted over the table rather than through a read-back, because the claim is about the
  // columns the statement did *not* write: an UPDATE that also reset the name, the active
  // state or the placement — or that reached the second Órgano — still satisfies a findById.
  @Test
  void updateImportable_sets_then_clears_the_mark_and_writes_nothing_else() throws Exception {
    TermoId termoId = insertTermo("Deportes", null);
    OrganoId id = insertOrgano("consorcio-x", "Consorcio X", true, false, termoId);
    insertOrgano("axencia-y", "Axencia Y", false, true, null);

    organoRepository.updateImportable(id, true);
    assertCatalogue(
        row("Axencia Y", false, true, null),
        row("Consorcio X", true, true, termoId));

    organoRepository.updateImportable(id, false);
    assertCatalogue(
        row("Axencia Y", false, true, null),
        row("Consorcio X", true, false, termoId));
  }

  @Test
  void eligible_organos_are_the_active_and_marked_ones_only() throws Exception {
    insertOrgano("eligible", "Eligible", true, true, null);
    insertOrgano("marked-but-inactive", "Marked But Inactive", false, true, null);
    insertOrgano("active-but-unmarked", "Active But Unmarked", true, false, null);
    insertOrgano("neither", "Neither", false, false, null);

    List<OrganoDeContratacion> eligible = organoRepository.findAllByActiveTrueAndImportableTrue();

    assertThat(eligible)
        .extracting(OrganoDeContratacion::sourceKey)
        .containsExactly("eligible");
  }

  private void assertCatalogue(CatalogueRow... expected) {
    Table organos = AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("organo_contratacion")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
    assertThat(organos).hasNumberOfRows(expected.length);
    for (int index = 0; index < expected.length; index++) {
      CatalogueRow row = expected[index];
      assertThat(organos).row(index).value("name").isEqualTo(row.name());
      assertThat(organos).row(index).value("active").isEqualTo(row.active());
      assertThat(organos).row(index).value("importable").isEqualTo(row.importable());
      UUID expectedTermoId = row.termoId();
      if (expectedTermoId == null) {
        assertThat(organos).row(index).value("termo_id").isNull();
      } else {
        assertThat(organos).row(index).value("termo_id").isEqualTo(expectedTermoId);
      }
    }
  }

  private static CatalogueRow row(
      String name, boolean active, boolean importable, @Nullable TermoId termoId) {
    return new CatalogueRow(
        name, active, importable, termoId == null ? null : termoId.value());
  }

  private record CatalogueRow(
      String name, boolean active, boolean importable, @Nullable UUID termoId) {}

  private List<Tuple> placementsByName() {
    return organoRepository.findAllOrderByName()
        .stream()
        .map(organo -> tuple(organo.name(), organo.termoId()))
        .toList();
  }

  // Neither helper below commits or rolls back: every test here only expects successful
  // inserts, and the injected DataSource's shared connection lets the repository calls
  // under test see these uncommitted writes within the same transaction. Contrast
  // TermoMigrationIntegrationTest's insertTermo, which does commit/rollback because
  // several of its tests deliberately trigger a constraint violation.
  private OrganoId insertOrgano(String sourceKey, String name, boolean active) throws Exception {
    return insertOrgano(sourceKey, name, active, null);
  }

  private OrganoId insertOrgano(String sourceKey, String name, boolean active,
      @Nullable TermoId termoId) throws Exception {
    return insertOrgano(sourceKey, name, active, false, termoId);
  }

  private OrganoId insertOrgano(String sourceKey, String name, boolean active, boolean importable,
      @Nullable TermoId termoId) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable, termo_id) "
            + "VALUES (uuidv7(), ?, ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setBoolean(3, active);
      statement.setBoolean(4, importable);
      statement.setObject(5, termoId == null ? null : termoId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  private TermoId insertTermo(String name, @Nullable TermoId parentId) throws Exception {
    String sql =
        "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setObject(2, parentId == null ? null : parentId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new TermoId(resultSet.getObject("id", UUID.class));
      }
    }
  }
}
