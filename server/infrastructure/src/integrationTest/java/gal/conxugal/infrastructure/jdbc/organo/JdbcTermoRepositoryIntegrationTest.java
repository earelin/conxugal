package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.DuplicateSiblingNameException;
import gal.conxugal.domain.organo.Termo;
import gal.conxugal.domain.organo.TermoNotFoundException;
import gal.conxugal.domain.organo.TermoRepository;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTermoRepositoryIntegrationTest implements TestPropertyProvider {

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
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE organo_contratacion, termo");
    }
  }

  @Test
  void round_trips_several_levels_of_nesting_as_flat_parent_edges() throws Exception {
    UUID rootId = insertTermo("Deportes", null);
    UUID childId = insertTermo("Fútbol", rootId);
    insertTermo("Liga", childId);

    List<Termo> termos = termoRepository.findAllOrderByName();

    assertThat(termos)
        .extracting(Termo::name, Termo::parentId)
        .containsExactly(
            tuple("Deportes", null),
            tuple("Fútbol", rootId),
            tuple("Liga", childId));
  }

  @Test
  void orders_accented_names_under_the_galician_collation() throws Exception {
    insertTermo("Zamora", null);
    insertTermo("Ávila", null);
    insertTermo("Avión", null);

    List<Termo> termos = termoRepository.findAllOrderByName();

    // Under the cluster default this returns Avión, Zamora, Ávila — the accent sorting
    // after Z is exactly what the column's collation exists to prevent.
    assertThat(termos)
        .extracting(Termo::name)
        .containsExactly("Ávila", "Avión", "Zamora");
  }

  @Test
  void inserts_termo_with_database_generated_id() {
    Termo created = termoRepository.insert(new Termo("Deportes", null));

    assertThat(created.id()).isNotNull();
    assertThat(termoRepository.findAllOrderByName())
        .extracting(Termo::name, Termo::parentId)
        .containsExactly(tuple("Deportes", null));
  }

  @Test
  void finds_stored_termo_by_id() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    UUID id = insertTermo("Fútbol", parentId);

    Termo found = termoRepository.findById(id).orElseThrow();

    assertThat(found.id()).isEqualTo(id);
    assertThat(found.name()).isEqualTo("Fútbol");
    assertThat(found.parentId()).isEqualTo(parentId);
  }

  @Test
  void finds_nothing_for_an_unknown_id() {
    assertThat(termoRepository.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void renames_termo_leaving_its_parent_untouched() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    UUID id = insertTermo("Fútbol", parentId);

    termoRepository.updateName(id, "Fútbol Sala");

    Termo renamed = termoRepository.findById(id).orElseThrow();
    assertThat(renamed.name()).isEqualTo("Fútbol Sala");
    assertThat(renamed.parentId()).isEqualTo(parentId);
  }

  @Test
  void re_parents_termo_between_parents_and_then_to_the_root() throws Exception {
    UUID firstParent = insertTermo("Deportes", null);
    UUID secondParent = insertTermo("Cultura", null);
    UUID id = insertTermo("Fútbol", firstParent);

    termoRepository.updateParentId(id, secondParent);
    assertThat(termoRepository.findById(id).orElseThrow().parentId()).isEqualTo(secondParent);

    termoRepository.updateParentId(id, null);
    assertThat(termoRepository.findById(id).orElseThrow().parentId()).isNull();
  }

  @Test
  void deletes_exactly_one_termo() throws Exception {
    UUID id = insertTermo("Deportes", null);
    insertTermo("Cultura", null);

    termoRepository.deleteById(id);

    assertThat(termoRepository.findAllOrderByName())
        .extracting(Termo::name)
        .containsExactly("Cultura");
  }

  @Test
  void reports_children_for_parent_and_none_for_leaf() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    UUID leafId = insertTermo("Fútbol", parentId);

    assertThat(termoRepository.existsByParentId(parentId)).isTrue();
    assertThat(termoRepository.existsByParentId(leafId)).isFalse();
  }

  @Test
  void finds_the_direct_children_of_parent() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    UUID childId = insertTermo("Fútbol", parentId);
    insertTermo("Liga", childId);
    insertTermo("Cultura", null);

    List<Termo> children = termoRepository.findByParentId(parentId);

    assertThat(children)
        .extracting(Termo::name)
        .containsExactly("Fútbol");
  }

  @Test
  void finds_the_roots_for_null_parent() throws Exception {
    UUID rootId = insertTermo("Deportes", null);
    insertTermo("Cultura", null);
    insertTermo("Fútbol", rootId);

    List<Termo> roots = termoRepository.findByParentId(null);

    // A derived query would bind `parent_id = null` and match nothing at all, silently
    // disabling the sibling-name rule for roots rather than failing loudly.
    assertThat(roots)
        .extracting(Termo::name)
        .containsExactlyInAnyOrder("Deportes", "Cultura");
  }

  @Test
  void inserting_duplicate_sibling_name_raises_the_domain_refusal() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);
    commitSharedConnection();

    assertThatThrownBy(() -> termoRepository.insert(new Termo("Fútbol", parentId)))
        .isInstanceOfSatisfying(
            DuplicateSiblingNameException.class,
            refusal -> assertThat(refusal.getName()).isEqualTo("Fútbol"));

    rollbackSharedConnection();
  }

  @Test
  void renaming_onto_sibling_name_raises_the_domain_refusal() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);
    UUID id = insertTermo("Baloncesto", parentId);
    commitSharedConnection();

    assertThatThrownBy(() -> termoRepository.updateName(id, "Fútbol"))
        .isInstanceOfSatisfying(
            DuplicateSiblingNameException.class,
            refusal -> assertThat(refusal.getName()).isEqualTo("Fútbol"));

    rollbackSharedConnection();
  }

  @Test
  void re_parenting_beside_same_named_sibling_raises_refusal_without_name()
      throws Exception {
    UUID firstParent = insertTermo("Deportes", null);
    UUID secondParent = insertTermo("Cultura", null);
    insertTermo("Fútbol", firstParent);
    UUID movedId = insertTermo("Fútbol", secondParent);
    commitSharedConnection();

    assertThatThrownBy(() -> termoRepository.updateParentId(movedId, firstParent))
        .isInstanceOfSatisfying(
            DuplicateSiblingNameException.class,
            // The move is addressed by ids alone, so the refusal cannot name the collision.
            refusal -> assertThat(refusal.getName()).isNull());

    rollbackSharedConnection();
  }

  @Test
  void inserting_under_an_unknown_parent_raises_termo_not_found() throws Exception {
    UUID unknownParent = UUID.randomUUID();
    commitSharedConnection();

    assertThatThrownBy(() -> termoRepository.insert(new Termo("Fútbol", unknownParent)))
        .isInstanceOfSatisfying(
            TermoNotFoundException.class,
            refusal -> assertThat(refusal.getTermoId()).isEqualTo(unknownParent));

    rollbackSharedConnection();
  }

  @Test
  void deleting_termo_whose_placements_were_not_cleared_is_refused() throws Exception {
    UUID termoId = insertTermo("Deportes", null);
    insertOrgano("consorcio-x", "Consorcio X", termoId);
    commitSharedConnection();

    // The foreign key carries no ON DELETE action, so the placement blocks the delete rather
    // than being nulled or taking the Órgano with it — the backstop for a skipped clearing.
    assertThatThrownBy(() -> termoRepository.deleteById(termoId))
        .isInstanceOfSatisfying(
            TermoNotFoundException.class,
            refusal -> assertThat(refusal.getTermoId()).isEqualTo(termoId));

    rollbackSharedConnection();

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    assertThat(assertDbConnection.table("termo").build()).hasNumberOfRows(1);
    assertThat(assertDbConnection.table("organo_contratacion").build()).hasNumberOfRows(1);
  }

  @Test
  void deleting_termo_that_still_has_children_is_refused() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    insertTermo("Fútbol", parentId);
    commitSharedConnection();

    assertThatThrownBy(() -> termoRepository.deleteById(parentId))
        .isInstanceOf(TermoNotFoundException.class);

    rollbackSharedConnection();

    AssertDbConnection assertDbConnection = AssertDbConnectionFactory.of(dataSource).create();
    assertThat(assertDbConnection.table("termo").build()).hasNumberOfRows(2);
  }

  // The injected DataSource is Micronaut Data's connection-context-aware proxy, so the raw
  // JDBC below and the repository call under test share one underlying connection. Committing
  // before a deliberate violation is what lets the fixture survive the rollback that the
  // aborted statement forces; rolling back afterwards is what lets any later statement —
  // including the @AfterEach truncate — run on that connection at all.
  private void commitSharedConnection() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.commit();
    }
  }

  private void rollbackSharedConnection() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.rollback();
    }
  }

  private UUID insertTermo(String name, UUID parentId) throws Exception {
    String sql = "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
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

  private void insertOrgano(String sourceKey, String name, UUID termoId) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, termo_id) "
            + "VALUES (uuidv7(), ?, ?, TRUE, ?)";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, name);
      statement.setObject(3, termoId);
      statement.executeUpdate();
    }
  }
}
