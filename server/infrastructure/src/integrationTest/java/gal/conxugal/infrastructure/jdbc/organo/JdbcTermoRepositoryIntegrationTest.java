package gal.conxugal.infrastructure.jdbc.organo;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.organo.taxonomia.Termo;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import gal.conxugal.domain.organo.taxonomia.TermoRepository;
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
    TermoId rootId = insertTermo("Deportes", null);
    TermoId childId = insertTermo("Fútbol", rootId);
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

    assertThat(created.id()).isNotNull();
    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    // Matching the stored id against the returned one is what proves the latter is the id
    // the database assigned, rather than merely non-null.
    assertThat(termos)
        .row(0)
            .value("id").isEqualTo(requireNonNull(created.id()).value())
            .value("name").isEqualTo("Deportes")
            .value("parent_id").isNull();
  }

  @Test
  void inserts_child_termo_under_an_existing_parent() {
    Termo parent = termoRepository.insert(new Termo("Deportes", null));

    Termo child = termoRepository.insert(new Termo("Fútbol", parent.id()));

    // The one insert the generated statement has to carry parent_id on.
    assertThat(termoTable())
        .row(1)
            .value("id").isEqualTo(requireNonNull(child.id()).value())
            .value("name").isEqualTo("Fútbol")
            .value("parent_id").isEqualTo(requireNonNull(parent.id()).value());
  }

  @Test
  void finds_stored_termo_by_id() throws Exception {
    TermoId parentId = insertTermo("Deportes", null);
    TermoId id = insertTermo("Fútbol", parentId);

    Termo found = termoRepository.findById(id).orElseThrow();

    assertThat(found.id()).isEqualTo(id);
    assertThat(found.name()).isEqualTo("Fútbol");
    assertThat(found.parentId()).isEqualTo(parentId);
  }

  @Test
  void finds_nothing_for_an_unknown_id() {
    assertThat(termoRepository.findById(new TermoId(UUID.randomUUID()))).isEmpty();
  }

  @Test
  void renames_termo_leaving_its_parent_untouched() throws Exception {
    TermoId parentId = insertTermo("Deportes", null);
    TermoId id = insertTermo("Fútbol", parentId);

    termoRepository.updateName(id, "Fútbol Sala");

    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(2);
    assertThat(termos)
        .row(0)
            .value("name").isEqualTo("Deportes")
            .value("parent_id").isNull()
        .row(1)
            .value("id").isEqualTo(id.value())
            .value("name").isEqualTo("Fútbol Sala")
            .value("parent_id").isEqualTo(parentId.value());
  }

  @Test
  void re_parents_termo_between_parents_and_then_to_the_root() throws Exception {
    TermoId firstParent = insertTermo("Deportes", null);
    TermoId secondParent = insertTermo("Cultura", null);
    TermoId id = insertTermo("Fútbol", firstParent);

    termoRepository.updateParentId(id, secondParent);

    Table afterMove = termoTable();
    assertThat(afterMove).hasNumberOfRows(3);
    // Asserting the two roots here rather than after the move to the root, where they are
    // already null and would pass anyway.
    assertThat(afterMove)
        .row(0)
            .value("id").isEqualTo(secondParent.value())
            .value("parent_id").isNull()
        .row(1)
            .value("id").isEqualTo(firstParent.value())
            .value("parent_id").isNull()
        .row(2)
            .value("id").isEqualTo(id.value())
            .value("parent_id").isEqualTo(secondParent.value());

    termoRepository.updateParentId(id, null);

    assertThat(termoTable())
        .row(2)
            .value("id").isEqualTo(id.value())
            .value("parent_id").isNull();
  }

  @Test
  void deletes_exactly_one_termo() throws Exception {
    TermoId id = insertTermo("Deportes", null);
    insertTermo("Cultura", null);

    termoRepository.deleteById(id);

    Table termos = termoTable();
    assertThat(termos).hasNumberOfRows(1);
    assertThat(termos)
        .row(0)
            .value("name").isEqualTo("Cultura");
  }

  @Test
  void reports_children_for_parent_and_none_for_leaf() throws Exception {
    TermoId parentId = insertTermo("Deportes", null);
    TermoId leafId = insertTermo("Fútbol", parentId);

    assertThat(termoRepository.existsByParentId(parentId)).isTrue();
    assertThat(termoRepository.existsByParentId(leafId)).isFalse();
    // The read CreateTermo makes before adding the first child under a parent.
    assertThat(termoRepository.findByParentId(leafId)).isEmpty();
  }

  @Test
  void finds_the_direct_children_of_parent() throws Exception {
    TermoId parentId = insertTermo("Deportes", null);
    TermoId childId = insertTermo("Fútbol", parentId);
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
    TermoId rootId = insertTermo("Deportes", null);
    insertTermo("Cultura", null);
    insertTermo("Fútbol", rootId);

    List<Termo> roots = termoRepository.findByParentId(null);

    assertThat(roots)
        .extracting(Termo::name, Termo::parentId)
        .containsExactlyInAnyOrder(tuple("Deportes", null), tuple("Cultura", null));
  }

  // Ordered by name so row(n) is stable, rather than leaning on uuidv7 insertion order.
  private Table termoTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("termo")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("name")})
        .build();
  }

  // This helper neither commits nor rolls back, because every test here expects its writes
  // to succeed: the injected DataSource is Micronaut Data's connection-context-aware proxy,
  // so these writes and the repository call under test share one connection and one
  // transaction, and the repository sees them uncommitted. A test that deliberately
  // triggers a constraint violation cannot use it as-is — an aborted statement poisons that
  // shared connection until it is rolled back, including for the next test's @AfterEach
  // truncate. See TermoMigrationIntegrationTest's commit/rollbackQuietly variant.
  private TermoId insertTermo(String name, @Nullable TermoId parentId) throws Exception {
    String sql = "INSERT INTO termo (id, name, parent_id) VALUES (uuidv7(), ?, ?) RETURNING id";
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
