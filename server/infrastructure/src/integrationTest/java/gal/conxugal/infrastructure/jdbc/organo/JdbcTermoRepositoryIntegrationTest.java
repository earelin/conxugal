package gal.conxugal.infrastructure.jdbc.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import gal.conxugal.domain.organo.Termo;
import gal.conxugal.domain.organo.TermoRepository;
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
  void inserts_root_termo_with_database_generated_id() {
    Termo created = termoRepository.insert(new Termo("Deportes", null));

    // Reading back by the returned id is what proves it is the id the database assigned,
    // rather than merely non-null.
    assertThat(created.id()).isNotNull();
    Termo stored = termoRepository.findById(created.id()).orElseThrow();
    assertThat(stored.name()).isEqualTo("Deportes");
    assertThat(stored.parentId()).isNull();
  }

  @Test
  void inserts_child_termo_under_an_existing_parent() {
    Termo parent = termoRepository.insert(new Termo("Deportes", null));

    Termo child = termoRepository.insert(new Termo("Fútbol", parent.id()));

    // The path CreateTermo takes for every term but the first, and the one the generated
    // INSERT has to carry parent_id on.
    assertThat(termoRepository.findById(child.id()).orElseThrow().parentId())
        .isEqualTo(parent.id());
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
    // A table-wide UPDATE would rename both rows and still satisfy the assertions above:
    // the two sit under different parents, so the sibling-name index would not object.
    assertThat(termoRepository.findById(parentId).orElseThrow().name()).isEqualTo("Deportes");
  }

  @Test
  void re_parents_termo_between_parents_and_then_to_the_root() throws Exception {
    UUID firstParent = insertTermo("Deportes", null);
    UUID secondParent = insertTermo("Cultura", null);
    UUID id = insertTermo("Fútbol", firstParent);

    termoRepository.updateParentId(id, secondParent);
    assertThat(termoRepository.findById(id).orElseThrow().parentId()).isEqualTo(secondParent);
    // The move left the old parent, and took nothing else with it: an unscoped UPDATE would
    // put every row under secondParent, including the two roots. Asserted here rather than
    // after the move to the root, where both roots are already null and would pass anyway.
    assertThat(termoRepository.findByParentId(firstParent)).isEmpty();
    assertThat(termoRepository.findById(firstParent).orElseThrow().parentId()).isNull();

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
    // The read CreateTermo makes before adding the first child under a parent.
    assertThat(termoRepository.findByParentId(leafId)).isEmpty();
  }

  @Test
  void finds_the_direct_children_of_parent() throws Exception {
    UUID parentId = insertTermo("Deportes", null);
    UUID childId = insertTermo("Fútbol", parentId);
    insertTermo("Liga", childId);
    insertTermo("Cultura", null);

    List<Termo> children = termoRepository.findByParentId(parentId);

    // Every column asserted, not just the name: this is the one hand-written SELECT *, so
    // its mapping onto Termo is a different code path from the derived reads. Were id or
    // parentId to arrive null, name alone would still match.
    assertThat(children)
        .extracting(Termo::id, Termo::name, Termo::parentId)
        .containsExactly(tuple(childId, "Fútbol", parentId));
  }

  @Test
  void finds_the_roots_for_null_parent() throws Exception {
    UUID rootId = insertTermo("Deportes", null);
    insertTermo("Cultura", null);
    insertTermo("Fútbol", rootId);

    List<Termo> roots = termoRepository.findByParentId(null);

    assertThat(roots)
        .extracting(Termo::name, Termo::parentId)
        .containsExactlyInAnyOrder(tuple("Deportes", null), tuple("Cultura", null));
  }

  // This helper neither commits nor rolls back, because every test here expects its writes
  // to succeed: the injected DataSource is Micronaut Data's connection-context-aware proxy,
  // so these writes and the repository call under test share one connection and one
  // transaction, and the repository sees them uncommitted. A test that deliberately
  // triggers a constraint violation cannot use it as-is — an aborted statement poisons that
  // shared connection until it is rolled back, including for the next test's @AfterEach
  // truncate. See TermoMigrationIntegrationTest's commit/rollbackQuietly variant.
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
}
