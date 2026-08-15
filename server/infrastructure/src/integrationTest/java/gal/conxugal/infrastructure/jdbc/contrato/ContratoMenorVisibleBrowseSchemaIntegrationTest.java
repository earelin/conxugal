package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies what the browse schema is <em>for</em>: not that two indexes exist, but that the four
 * orderings, the count and the year facets can each be answered from one of them without sorting
 * and, where the criterion says so, without touching the table. An index nobody's plan reaches is
 * write cost and nothing else, and only a plan can say which it is.
 *
 * <p><strong>The statements below read {@code contrato_menor} alone.</strong> The paged read that
 * follows joins {@code operador_economico} for the awardee, and its {@code WHERE} is required to
 * stay byte-identical to the one written here — but the join cannot change what the index has to
 * produce, and leaving it out is what lets the count and the facets be asserted as reaching no
 * heap at all. What that read adds, its own tests prove.
 *
 * <p>Plans are taken with <strong>sequential and bitmap scans off</strong>. Both are ways of
 * reading a whole selection and sorting it afterwards, and on a selection this size that is
 * genuinely the cheaper plan — so leaving either on would measure the fixture's row count rather
 * than the schema. What the two flags leave the planner is the choice this test is about: read one
 * index in order, or read one and sort. An ordering no index can produce still picks the second
 * and still shows a sort node, which is what makes the assertion mean anything.
 *
 * <p>They are also taken on a <strong>raw connection off the container</strong> rather than the
 * injected {@code DataSource}. {@code VACUUM} cannot run inside a transaction and Micronaut Data's
 * connection-context-aware proxy has autocommit off — and {@code VACUUM} is not optional here:
 * {@code ANALYZE} gives the planner its statistics, but only {@code VACUUM} sets the visibility
 * map, without which an index-only scan still reports heap fetches and the assertion that matters
 * most would fail for a reason that has nothing to do with the index.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratoMenorVisibleBrowseSchemaIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private static final UUID BUSY_ORGANO = UUID.fromString("0192a000-0000-7000-8000-000000000001");
  private static final UUID OTHER_ORGANO = UUID.fromString("0192a000-0000-7000-8000-000000000002");
  private static final UUID AWARDEE = UUID.fromString("0192a000-0000-7000-8000-0000000000f1");
  private static final int BROWSED_YEAR = 2025;

  // source_id is organo base + (year - 2000) * 1000 + n, so a seeded contract can be named back.
  private static final long FIRST_BUSY_2025 = 125001L;
  private static final long FIRST_BUSY_2024 = 124001L;
  private static final long UNDATED_ANOMALY = 900003L;

  private static final String DATE_INDEX = "contrato_menor_organo_year_date_idx";
  private static final String AMOUNT_INDEX = "contrato_menor_organo_year_amount_idx";
  private static final String REPLACED_INDEX = "contrato_menor_organo_id_publication_date_idx";
  private static final String VISIBILITY_PREDICATE =
      "WHERE ((amount IS NOT NULL) AND (operador_economico_id IS NOT NULL))";

  /**
   * The definition of <em>visible</em>, and the two indexes' partial predicate, written once. The
   * date needs no conjunct: {@code publication_year} is null exactly when {@code publication_date}
   * is, so the equality test already withholds an undated contract.
   */
  private static final String VISIBLE_SELECTION =
      """
        FROM contrato_menor
       WHERE organo_id = ?
         AND publication_year = ?
         AND amount IS NOT NULL
         AND operador_economico_id IS NOT NULL
      """;

  private static final String SELECTED_COLUMNS =
      """
      SELECT source_id, publication_date, obxecto, amount, duration
      """;

  private static final String DATE_ASCENDING =
      SELECTED_COLUMNS + VISIBLE_SELECTION + " ORDER BY publication_date, source_id LIMIT 50";
  private static final String DATE_DESCENDING =
      SELECTED_COLUMNS + VISIBLE_SELECTION
          + " ORDER BY publication_date DESC, source_id DESC LIMIT 50";
  private static final String AMOUNT_ASCENDING =
      SELECTED_COLUMNS + VISIBLE_SELECTION + " ORDER BY amount, source_id LIMIT 50";
  private static final String AMOUNT_DESCENDING =
      SELECTED_COLUMNS + VISIBLE_SELECTION + " ORDER BY amount DESC, source_id DESC LIMIT 50";

  private static final String SELECTION_COUNT =
      """
      SELECT COUNT(*)
      """
          + VISIBLE_SELECTION;

  private static final String YEAR_FACETS =
      """
      SELECT DISTINCT publication_year
        FROM contrato_menor
       WHERE organo_id = ?
         AND amount IS NOT NULL
         AND operador_economico_id IS NOT NULL
       ORDER BY publication_year DESC
      """;

  /** Verbatim from the adapter FEAT-0012 shipped, whose index this migration drops. */
  private static final String VISIBLE_ORGANOS =
      """
      SELECT candidate.id FROM unnest(?::uuid[]) AS candidate(id)
      WHERE EXISTS (
          SELECT 1 FROM contrato_menor
           WHERE organo_id = candidate.id
             AND publication_date IS NOT NULL
             AND amount IS NOT NULL
             AND operador_economico_id IS NOT NULL)
      """;

  private static final String SEED_ORGANOS =
      """
      INSERT INTO organo_contratacion (id, source_key, name, active)
      VALUES (?, 'consorcio-x', 'consorcio-x', TRUE), (?, 'axencia-y', 'axencia-y', TRUE)
      """;

  private static final String SEED_AWARDEE =
      """
      INSERT INTO operador_economico (id, fiscal_id, name, name_rank_source_id)
      VALUES (?, 'B00000001', 'Adxudicataria Un', 1)
      """;

  /**
   * Two Órganos over two years, with ties on both sorted values — twenty-four publication dates
   * and forty amounts across two hundred and forty contracts each — because an ordering that is
   * only total by accident of distinct values proves nothing about the tiebreaker, and a selection
   * smaller than the page asked for proves nothing about paging one.
   */
  private static final String SEED_CONTRACTS =
      """
      INSERT INTO contrato_menor (
          source_id, organo_id, publication_date, obxecto, amount, duration,
          operador_economico_id)
      SELECT organo.base + (chosen.year - 2000) * 1000 + n,
             organo.id,
             make_date(chosen.year, 1, 1) + ((n % 24) * 15),
             'obxecto ' || n,
             ((n % 40) * 250)::numeric,
             '1 mes',
             ?
        FROM (VALUES (?::uuid, 100000), (?::uuid, 200000)) AS organo(id, base)
       CROSS JOIN (VALUES (2024), (2025)) AS chosen(year)
       CROSS JOIN generate_series(1, 240) AS n
      """;

  /** The four R28 withholds: no amount, no awardee, no date, and none of the three. */
  private static final String SEED_ANOMALIES =
      """
      INSERT INTO contrato_menor (
          source_id, organo_id, publication_date, amount, operador_economico_id)
      SELECT anomaly.source_id, ?, anomaly.publication_date, anomaly.amount,
             CASE WHEN anomaly.awarded THEN ?::uuid END
        FROM (VALUES (900001, DATE '2025-06-01', NULL::numeric, TRUE),
                     (900002, DATE '2025-06-01', 500::numeric, FALSE),
                     (900003, NULL::date, 500::numeric, TRUE),
                     (900004, NULL::date, NULL::numeric, FALSE))
             AS anomaly(source_id, publication_date, amount, awarded)
      """;

  private static final String EXPLAIN = "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF, TIMING OFF) ";

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  DataSource dataSource;

  private Connection connection;

  @BeforeEach
  void seed() throws Exception {
    connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    execute(SEED_ORGANOS, BUSY_ORGANO, OTHER_ORGANO);
    execute(SEED_AWARDEE, AWARDEE);
    execute(SEED_CONTRACTS, AWARDEE, BUSY_ORGANO, OTHER_ORGANO);
    execute(SEED_ANOMALIES, BUSY_ORGANO, AWARDEE);
    try (Statement statement = connection.createStatement()) {
      statement.execute("VACUUM (ANALYZE) contrato_menor");
    }
  }

  // The truncate goes through the injected DataSource on purpose: Micronaut Data's proxy shares
  // one underlying connection with autocommit off, and the commit inside is what leaves it holding
  // no snapshot — a connection idle in a transaction would stop the next test's VACUUM marking
  // pages all-visible, and the heap-fetch assertions would fail for a reason unrelated to any
  // index.
  @AfterEach
  void cleanUp() throws Exception {
    connection.close();
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void date_ascending_scans_the_date_index_forward_with_no_sort_node() throws Exception {
    assertThat(planOf(EXPLAIN + DATE_ASCENDING, BUSY_ORGANO, BROWSED_YEAR))
        .contains("Index Scan using " + DATE_INDEX)
        .doesNotContain("Sort");
  }

  // The tiebreaker descends with the key it breaks ties for. Ending source_id ASC here would be
  // the reverse of nothing a B-tree holds, and the sort node this asserts against is what the
  // planner would answer with.
  @Test
  void date_descending_scans_the_date_index_backward_with_no_sort_node() throws Exception {
    assertThat(planOf(EXPLAIN + DATE_DESCENDING, BUSY_ORGANO, BROWSED_YEAR))
        .contains("Index Scan Backward using " + DATE_INDEX)
        .doesNotContain("Sort");
  }

  @Test
  void amount_ascending_scans_the_amount_index_forward_with_no_sort_node() throws Exception {
    assertThat(planOf(EXPLAIN + AMOUNT_ASCENDING, BUSY_ORGANO, BROWSED_YEAR))
        .contains("Index Scan using " + AMOUNT_INDEX)
        .doesNotContain("Sort");
  }

  // R24's named read. One index serves both amount directions because R28 leaves no null amount
  // in the visible set to place, so this is a plain backward scan rather than a second index
  // carrying NULLS LAST.
  @Test
  void amount_descending_scans_the_amount_index_backward_with_no_sort_node() throws Exception {
    assertThat(planOf(EXPLAIN + AMOUNT_DESCENDING, BUSY_ORGANO, BROWSED_YEAR))
        .contains("Index Scan Backward using " + AMOUNT_INDEX)
        .doesNotContain("Sort");
  }

  // Either index answers this: both lead with the two equality columns, and which one the planner
  // costs lower is its own business rather than a guarantee to pin. What is pinned is that the
  // count never reaches the table, which is what the partial predicate buys.
  @Test
  void the_selection_count_answers_from_the_index_without_touching_the_heap() throws Exception {
    assertThat(planOf(EXPLAIN + SELECTION_COUNT, BUSY_ORGANO, BROWSED_YEAR))
        .containsPattern("Index Only Scan using contrato_menor_organo_year_(date|amount)_idx")
        .contains("Heap Fetches: 0");
  }

  @Test
  void the_year_facets_answer_from_an_index_only_scan_with_no_heap_fetch() throws Exception {
    assertThat(planOf(EXPLAIN + YEAR_FACETS, BUSY_ORGANO))
        .containsPattern(
            "Index Only Scan Backward using contrato_menor_organo_year_(date|amount)_idx")
        .contains("Heap Fetches: 0")
        .doesNotContain("Sort");
  }

  // The one shipped read whose index this migration drops. It is the date index that has to serve
  // it, and forced rather than chosen: only that one carries publication_date, so only it can
  // answer the third null check without fetching the row.
  @Test
  void the_visible_organos_semi_join_keeps_its_index_when_the_old_one_goes() throws Exception {
    Array candidates = connection.createArrayOf("uuid", new UUID[] {BUSY_ORGANO, OTHER_ORGANO});

    assertThat(planOf(EXPLAIN + VISIBLE_ORGANOS, candidates))
        .contains("Index Only Scan using " + DATE_INDEX)
        .contains("Heap Fetches: 0");
  }

  // attgenerated rather than information_schema's is_generated, which answers ALWAYS for a virtual
  // generated column too — and a virtual one is not indexable, so the distinction is the whole
  // point of the column.
  @Test
  void publication_year_is_generated_stored_and_integer_typed() throws Exception {
    assertThat(publicationYearColumn()).containsExactly("integer", "s");
  }

  @Test
  void publication_year_holds_the_year_of_the_publication_date_or_null() throws Exception {
    assertThat(publicationYearOf(FIRST_BUSY_2025)).isEqualTo(2025);
    assertThat(publicationYearOf(FIRST_BUSY_2024)).isEqualTo(2024);
    assertThat(publicationYearOf(UNDATED_ANOMALY)).isNull();
  }

  @Test
  void the_generated_column_refuses_any_statement_that_writes_it() {
    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO contrato_menor (source_id, organo_id, publication_year)"
                        + " VALUES (500001, ?, 2025)",
                    BUSY_ORGANO))
        .isInstanceOfSatisfying(
            SQLException.class,
            // SQLSTATE 428C9 is generated_always.
            exception -> assertThat(exception.getSQLState()).isEqualTo("428C9"));
  }

  @Test
  void the_browse_indexes_replace_the_one_the_first_of_them_subsumes() throws Exception {
    assertThat(indexNames())
        .contains("contrato_menor_operador_economico_id_idx", DATE_INDEX, AMOUNT_INDEX)
        .doesNotContain(REPLACED_INDEX);
  }

  // Widening either index later would still pass every plan assertion above while silently costing
  // the facets and the count their index-only scan, so the predicate itself is pinned.
  @Test
  void both_browse_indexes_are_partial_on_the_visibility_predicate() throws Exception {
    assertThat(definitionOf(DATE_INDEX)).endsWith(VISIBILITY_PREDICATE);
    assertThat(definitionOf(AMOUNT_INDEX)).endsWith(VISIBILITY_PREDICATE);
  }

  private String planOf(String sql, Object... parameters) throws SQLException {
    connection.setAutoCommit(false);
    try (Statement session = connection.createStatement()) {
      session.execute("SET LOCAL enable_seqscan = off");
      session.execute("SET LOCAL enable_bitmapscan = off");
      List<String> lines = new ArrayList<>();
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        bind(statement, parameters);
        try (ResultSet rows = statement.executeQuery()) {
          while (rows.next()) {
            lines.add(rows.getString(1));
          }
        }
      }
      return String.join("\n", lines);
    } finally {
      connection.rollback();
      connection.setAutoCommit(true);
    }
  }

  private List<String> publicationYearColumn() throws SQLException {
    String sql =
        """
        SELECT format_type(atttypid, atttypmod) AS type, attgenerated
          FROM pg_attribute
         WHERE attrelid = 'contrato_menor'::regclass AND attname = 'publication_year'
        """;
    List<String> column = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) {
        column.add(rows.getString("type"));
        column.add(rows.getString("attgenerated"));
      }
    }
    return column;
  }

  private Integer publicationYearOf(long sourceId) throws SQLException {
    String sql = "SELECT publication_year FROM contrato_menor WHERE source_id = ?";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setLong(1, sourceId);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException(
              "No contract stored under source id %d".formatted(sourceId));
        }
        int year = rows.getInt("publication_year");
        return rows.wasNull() ? null : year;
      }
    }
  }

  private List<String> indexNames() throws SQLException {
    List<String> names = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'contrato_menor'")) {
      while (rows.next()) {
        names.add(rows.getString("indexname"));
      }
    }
    return names;
  }

  private String definitionOf(String indexName) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT indexdef FROM pg_indexes WHERE indexname = ?")) {
      statement.setString(1, indexName);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("No index named %s".formatted(indexName));
        }
        return rows.getString("indexdef");
      }
    }
  }

  private void execute(String sql, Object... parameters) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, parameters);
      statement.executeUpdate();
    }
  }

  private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index] instanceof Array array) {
        statement.setArray(index + 1, array);
      } else {
        statement.setObject(index + 1, parameters[index]);
      }
    }
  }
}
