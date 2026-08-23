package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorId;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.UpsertCounts;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.contrato.VisibleContratoMenorRepository;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganosWithVisibleContracts;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Stores contratos menores. {@code countByOrganoId} derives from its own name; the batch upsert
 * cannot, so it is written out below.
 *
 * <p>It is <strong>one statement per batch</strong>, matching on the source identifier and
 * refreshing in place — never delete-and-reinsert — so a re-imported contract keeps its row and
 * the identity assigned to it the first time. {@code xmax} is what tells the two branches apart:
 * PostgreSQL leaves it zero on a row this statement inserted and non-zero on one it updated, so
 * the added and refreshed counts come out of the write itself rather than out of a second read of
 * the whole batch.
 *
 * <p>The rows travel as parallel arrays through {@code unnest} rather than as a {@code VALUES}
 * list built per batch, which keeps the statement a constant: one prepared form whatever the
 * batch size, rather than a string assembled around a placeholder count.
 *
 * <p>A page repeating a publication is absorbed before the statement runs: PostgreSQL refuses an
 * {@code ON CONFLICT DO UPDATE} that would touch one row twice, and that refusal is deterministic,
 * so a single repeated row on one page would fail the same way on every retry and block that
 * Órgano's history for good. The last reading of a source identifier wins, which is the rule the
 * upsert already applies across batches, applied within one.
 *
 * <p>{@code operador_economico_id} is refreshed like every other published value, which is what
 * makes a correction changing a contract's published fiscal identifier repoint the row at the
 * operador the corrected identifier names. Leaving it out of the update would let a conflicting row
 * keep an awardee its publication no longer names, silently and for good — the caller resolves the
 * awardee on every upsert precisely so that it does not.
 *
 * <p>It is also this family's answer to <em>which Órganos hold a visible contract</em>. Visible
 * means complete, and complete means all three of a publication date, an amount and an awardee:
 * without a date there is no year's list the contract could appear in, without an amount it
 * answers none of the questions a reader is here to ask, and without an awardee it names nobody it
 * was awarded to. A contract missing any one of them is stored as an anomaly and places its
 * Órgano in nobody's visible set.
 *
 * <p>Reading a browse page joins it here rather than in an adapter of its own: a page of contratos
 * menores and a batch of them are two questions of one table, and the visibility rule they both
 * state is the same rule. The years an Órgano has visible contracts in join it for the same
 * reason, and they are the one place that rule has to be written a second way — a facet read has
 * no year to test for equality, so it is the only one that must exclude the null year itself.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcContratoMenorRepository
    implements ContratoMenorRepository,
        OrganosWithVisibleContracts,
        VisibleContratoMenorRepository,
        GenericRepository<ContratoMenor, ContratoMenorId> {

  private static final String UPSERT_SQL =
      """
      INSERT INTO contrato_menor (
          source_id, organo_id, publication_date, obxecto, amount, duration,
          operador_economico_id)
      SELECT * FROM unnest(
          ?::bigint[], ?::uuid[], ?::date[], ?::text[], ?::numeric[], ?::text[], ?::uuid[])
      ON CONFLICT (source_id) DO UPDATE SET
          organo_id = EXCLUDED.organo_id,
          publication_date = EXCLUDED.publication_date,
          obxecto = EXCLUDED.obxecto,
          amount = EXCLUDED.amount,
          duration = EXCLUDED.duration,
          operador_economico_id = EXCLUDED.operador_economico_id
      RETURNING (xmax = 0) AS inserted
      """;

  /**
   * A semi-join driven from the candidates rather than from the contracts, which leaves the planner
   * free to answer each one from an index and stop at its first visible contract. Its predecessor,
   * {@code SELECT DISTINCT organo_id ... WHERE organo_id IN (...)}, could not: an aggregate has to
   * read every qualifying row in a table headed for millions to answer a question about a few
   * hundred Órganos.
   *
   * <p><strong>The shape is the planner's choice, not this statement's guarantee.</strong> Where
   * almost no candidate holds anything it hash-joins instead and reads the table once — the work
   * the aggregate always did, and no worse. What this form removes is the floor, not the ceiling.
   *
   * <p>The candidates arrive as one {@code uuid[]} for the same reason the upsert's rows do: one
   * prepared form whatever the catalogue's size, rather than a statement whose placeholder count —
   * and so whose plan-cache entry — changes with it.
   *
   * <p>The three null checks are the visibility rule itself, and they are stated here rather than
   * left to an index because this read has no year to scope by: the browsing reads do, which is
   * what lets their indexes carry only the other two.
   */
  private static final String VISIBLE_ORGANOS_SQL =
      """
      SELECT candidate.id FROM unnest(?::uuid[]) AS candidate(id)
      WHERE EXISTS (
          SELECT 1 FROM contrato_menor
           WHERE organo_id = candidate.id
             AND publication_date IS NOT NULL
             AND amount IS NOT NULL
             AND operador_economico_id IS NOT NULL)
      """;

  /**
   * The definition of <em>visible</em>, not a filter bolted onto a read, and <strong>the whole of
   * what the page and the count have in common</strong> — written once so that a total cannot come
   * to disagree with the pages beneath it by losing a conjunct the page kept.
   *
   * <p>The date needs no conjunct: {@code publication_year} is null exactly when {@code
   * publication_date} is, so the equality test already withholds an undated contract. The other two
   * are explicit, and {@code operador_economico_id IS NOT NULL} is doing real work here rather than
   * restating the page's join — the count has no join to lean on. It is also what makes this match
   * the two browse indexes' partial predicate word for word, without which PostgreSQL cannot use
   * them.
   */
  private static final String VISIBLE_WHERE =
      """
       WHERE organo_id = ?
         AND publication_year = ?
         AND amount IS NOT NULL
         AND operador_economico_id IS NOT NULL
      """;

  /**
   * <strong>It carries no ordering</strong> — the {@code Pageable}'s is appended to it, so one that
   * ordered as well would emit two clauses. The awardee's two columns are aliased so that every
   * column a row is read by is named once, here, rather than the reader below knowing that one of
   * them is called {@code name} on the other table. None of the seven is ambiguous across the join.
   */
  private static final String VISIBLE_PAGE_SQL =
      """
      SELECT contrato_menor.source_id,
             contrato_menor.publication_date,
             contrato_menor.obxecto,
             contrato_menor.amount,
             contrato_menor.duration,
             operador_economico.name AS awardee_name,
             operador_economico.fiscal_id AS awardee_fiscal_id
        FROM contrato_menor
        JOIN operador_economico
          ON operador_economico.id = contrato_menor.operador_economico_id
      """
          // fiscal_id is nullable at the schema now -- one party is catalogued without one -- and
          // the row mapper below reads it as present. What holds is that this family reaches an
          // operador only by resolving a published identifier, so it can never join to a row that
          // has none. That guarantee moved from the column to the importer and is stated here
          // because nothing enforces it any more.
          + VISIBLE_WHERE;

  /**
   * <strong>The count does not join</strong>, and that is the difference between reading an index
   * and reading the table. The join is a no-op for a count — {@code operador_economico_id}
   * references a primary key and the predicate already excludes the null, so it can neither drop a
   * row nor duplicate one — but its presence takes the planner off the partial index and onto a
   * heap scan of every candidate row, plus a scan of the whole operador table, on every page a
   * reader turns. Sharing the predicate is what keeps the two statements honest with each other;
   * sharing the {@code FROM} would only make them equally slow.
   */
  private static final String VISIBLE_COUNT_SQL =
      """
      SELECT COUNT(*)
        FROM contrato_menor
      """
          + VISIBLE_WHERE;

  /**
   * <strong>The one browse read that has to exclude the null year itself.</strong> Everywhere else
   * the year is an equality test, which withholds an undated contract for free; here there is no
   * equality test to do it. A contract holding an amount and an awardee but no date is an anomaly
   * all the same, yet it satisfies both browse indexes' partial predicate and enters them under a
   * null year — and {@code DISTINCT} would answer that null <em>as a year</em>, first, since
   * {@code DESC} orders nulls ahead of every real one. So the section would open on a year that is
   * not one, and {@link YearSelection}, which has no representable absence, would refuse it: a
   * failed read rather than a stray entry in the chooser.
   *
   * <p>PostgreSQL turns the conjunct into an index condition, so the index-only scan the schema
   * test pins is unaffected by carrying it.
   *
   * <p>It does not share {@link #VISIBLE_WHERE}: that predicate's year equality is exactly what
   * this one replaces, so the two are different questions rather than one written twice.
   *
   * <p>The Órgano arrives by name rather than as a placeholder, which is what a mapped query takes;
   * the statement reaching PostgreSQL is the one the schema test pins the plan of, and this read's
   * own test asserts that character for character rather than trusting the substitution.
   */
  private static final String YEAR_FACETS_SQL =
      """
      SELECT DISTINCT publication_year
        FROM contrato_menor
       WHERE organo_id = :organoId
         AND publication_year IS NOT NULL
         AND amount IS NOT NULL
         AND operador_economico_id IS NOT NULL
       ORDER BY publication_year DESC
      """;

  // The predicate's two placeholders come first in both statements; the page's paging follows it.
  private static final int ORGANO_PARAMETER = 1;
  private static final int YEAR_PARAMETER = 2;
  private static final int LIMIT_PARAMETER = 3;
  private static final int OFFSET_PARAMETER = 4;

  /**
   * Every column a browse ordering may name, taken from {@link SortKey} rather than restated, so
   * the set cannot come to admit one the closed set of orderings could never produce. Both
   * directions are walked because nothing obliges a key to name the same column in each.
   */
  private static final Set<String> ORDERABLE_COLUMNS =
      Arrays.stream(Sort.Order.Direction.values())
          .flatMap(direction -> Arrays.stream(SortKey.values())
              .flatMap(key -> key.ordering(direction).getOrderBy().stream()))
          .map(Sort.Order::getProperty)
          .collect(Collectors.toUnmodifiableSet());

  private final JdbcOperations jdbcOperations;

  protected JdbcContratoMenorRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public UpsertCounts upsertAll(Collection<ContratoMenor> contratos) {
    if (contratos.isEmpty()) {
      return new UpsertCounts(0, 0);
    }
    List<ContratoMenor> batch = lastReadingPerSourceId(contratos);
    return jdbcOperations.prepareStatement(UPSERT_SQL, statement -> {
      bindBatch(statement, batch);
      return countBranches(statement);
    });
  }

  @Override
  public abstract long countByOrganoId(OrganoId organoId);

  /**
   * <strong>The one read here the framework can map, and the only one.</strong> A column of years
   * has no aggregate behind it to carry a mapping, so each value is rebuilt through the core
   * conversion service — the half {@link YearSelection}'s converter carries for exactly this — and
   * none of what stops the page being projected applies: there is one column, and it is not a
   * component of a record whose mapping is built outside the container.
   *
   * <p><strong>It carries no transaction, and the three methods below it do.</strong> A derived
   * method has run on a connection of its own rather than in a transaction since Micronaut Data 4,
   * which is all one statement needs; the hand-written reads are annotated because {@code
   * jdbcOperations} has to be handed a bound connection, and the paged one because its two
   * statements share it. {@code countByOrganoId} is derived and unannotated for this same reason.
   */
  @Override
  @Query(YEAR_FACETS_SQL)
  public abstract List<YearSelection> years(OrganoId organoId);

  /**
   * An empty candidate set short-circuits rather than reaching the database, where an array of no
   * elements would buy a round trip for an answer that is empty by construction.
   */
  @Override
  @Transactional(readOnly = true)
  public Set<OrganoId> among(Collection<OrganoId> candidates) {
    if (candidates.isEmpty()) {
      return Set.of();
    }
    UUID[] ids = candidates.stream().map(OrganoId::value).toArray(UUID[]::new);
    return jdbcOperations.prepareStatement(VISIBLE_ORGANOS_SQL, statement -> {
      statement.setArray(1, statement.getConnection().createArrayOf("uuid", ids));
      return readOrganoIds(statement);
    });
  }

  /**
   * The page and the count of the whole selection, from one predicate written once, so a total can
   * never come to disagree with the pages beneath it by losing a conjunct one of them kept.
   *
   * <p><strong>The rows are read by hand rather than projected.</strong> A {@code @Query} answering
   * {@code Page<VisibleContratoMenor>} is the smaller thing and was the first thing tried, and it
   * cannot carry this projection: Micronaut Data builds a projection's mapping outside the
   * container, so the moment a component's type names an {@code AttributeConverter} — the amount's
   * does, and the awardee's identifier's does — reading a row fails with <em>Converters not
   * supported</em>. The alternative was a projection of bare {@code BigDecimal} and {@code String},
   * which would move the two conversions to whoever reads the page and lose the guarantee the
   * types carry.
   *
   * <p>Reading by hand means the ordering is appended here rather than by the framework, and
   * appending a name to SQL is the one thing this design cannot do carelessly: on a native
   * statement a property name is interpolated verbatim and unescaped. {@link #orderBy} therefore
   * refuses any column {@link SortKey} could not have produced, which makes the closed set of
   * orderings a property of this statement rather than an obligation on its callers.
   *
   * <p>The total is of the whole selection and is always read: the port offers no page without
   * one, so a {@code Pageable} asking for none is answered with one anyway. The two statements
   * share a connection but not a snapshot — under {@code READ COMMITTED} each takes its own — so a
   * total is accurate as of a moment very slightly after the page it accompanies. An import
   * committing between them can leave the two describing states one row apart, which for a browse
   * list is a page that reappears on refresh rather than anything a reader can be misled by.
   */
  @Override
  @Transactional(readOnly = true)
  public Page<VisibleContratoMenor> page(
      OrganoId organoId, YearSelection year, Pageable pageable) {
    if (pageable.getSize() < 1) {
      throw new IllegalArgumentException(
          "A browse read is paged, and this one asks for %d rows".formatted(pageable.getSize()));
    }
    String sql = VISIBLE_PAGE_SQL + orderBy(pageable.getSort()) + " LIMIT ? OFFSET ?";
    List<VisibleContratoMenor> content = jdbcOperations.prepareStatement(sql, statement -> {
      bindSelection(statement, organoId, year);
      statement.setInt(LIMIT_PARAMETER, pageable.getSize());
      statement.setLong(OFFSET_PARAMETER, pageable.getOffset());
      return readVisible(statement);
    });
    long total = jdbcOperations.prepareStatement(VISIBLE_COUNT_SQL, statement -> {
      bindSelection(statement, organoId, year);
      return readCount(statement);
    });
    return Page.of(content, pageable, total);
  }

  private static String orderBy(Sort sort) {
    List<Sort.Order> orders = sort.getOrderBy();
    if (orders.isEmpty()) {
      throw new IllegalArgumentException("A browse read is ordered, and this one carries no sort");
    }
    return orders.stream()
        .map(JdbcContratoMenorRepository::orderTerm)
        .collect(Collectors.joining(", ", " ORDER BY ", ""));
  }

  private static String orderTerm(Sort.Order order) {
    if (!ORDERABLE_COLUMNS.contains(order.getProperty())) {
      throw new IllegalArgumentException(
          "No browse ordering names %s: it can only be one of %s"
              .formatted(order.getProperty(), ORDERABLE_COLUMNS));
    }
    return "%s %s".formatted(order.getProperty(), order.getDirection());
  }

  private static void bindSelection(
      PreparedStatement statement, OrganoId organoId, YearSelection year) throws SQLException {
    statement.setObject(ORGANO_PARAMETER, organoId.value());
    statement.setInt(YEAR_PARAMETER, year.year());
  }

  private static List<VisibleContratoMenor> readVisible(PreparedStatement statement)
      throws SQLException {
    List<VisibleContratoMenor> visible = new ArrayList<>();
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        visible.add(
            new VisibleContratoMenor(
                rows.getLong("source_id"),
                rows.getObject("publication_date", LocalDate.class),
                rows.getString("obxecto"),
                new Money(rows.getBigDecimal("amount")),
                rows.getString("duration"),
                rows.getString("awardee_name"),
                new FiscalIdentifier(rows.getString("awardee_fiscal_id"))));
      }
    }
    return List.copyOf(visible);
  }

  private static long readCount(PreparedStatement statement) throws SQLException {
    try (ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) {
        throw new IllegalStateException("A count answered no row at all");
      }
      return rows.getLong(1);
    }
  }

  private static List<ContratoMenor> lastReadingPerSourceId(Collection<ContratoMenor> contratos) {
    Map<Long, ContratoMenor> bySourceId = new LinkedHashMap<>();
    for (ContratoMenor contrato : contratos) {
      bySourceId.put(contrato.sourceId(), contrato);
    }
    return List.copyOf(bySourceId.values());
  }

  private static void bindBatch(PreparedStatement statement, List<ContratoMenor> batch)
      throws SQLException {
    Connection connection = statement.getConnection();
    int size = batch.size();
    Long[] sourceIds = new Long[size];
    UUID[] organoIds = new UUID[size];
    Date[] publicationDates = new Date[size];
    String[] obxectos = new String[size];
    BigDecimal[] amounts = new BigDecimal[size];
    String[] durations = new String[size];
    UUID[] operadorIds = new UUID[size];
    for (int index = 0; index < size; index++) {
      ContratoMenor contrato = batch.get(index);
      sourceIds[index] = contrato.sourceId();
      organoIds[index] = contrato.organoId().value();
      publicationDates[index] = toSqlDate(contrato.publicationDate());
      obxectos[index] = contrato.obxecto();
      amounts[index] = toBigDecimal(contrato.amount());
      durations[index] = contrato.duration();
      operadorIds[index] = toOperadorUuid(contrato.operadorEconomico());
    }
    statement.setArray(1, connection.createArrayOf("bigint", sourceIds));
    statement.setArray(2, connection.createArrayOf("uuid", organoIds));
    statement.setArray(3, connection.createArrayOf("date", publicationDates));
    statement.setArray(4, connection.createArrayOf("text", obxectos));
    statement.setArray(5, connection.createArrayOf("numeric", amounts));
    statement.setArray(6, connection.createArrayOf("text", durations));
    statement.setArray(7, connection.createArrayOf("uuid", operadorIds));
  }

  private static Set<OrganoId> readOrganoIds(PreparedStatement statement) throws SQLException {
    Set<OrganoId> visible = new HashSet<>();
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        visible.add(new OrganoId(rows.getObject("id", UUID.class)));
      }
    }
    return Set.copyOf(visible);
  }

  private static UpsertCounts countBranches(PreparedStatement statement) throws SQLException {
    int added = 0;
    int refreshed = 0;
    try (ResultSet rows = statement.executeQuery()) {
      while (rows.next()) {
        if (rows.getBoolean("inserted")) {
          added++;
        } else {
          refreshed++;
        }
      }
    }
    return new UpsertCounts(added, refreshed);
  }

  private static @Nullable Date toSqlDate(@Nullable LocalDate publicationDate) {
    return publicationDate == null ? null : Date.valueOf(publicationDate);
  }

  private static @Nullable BigDecimal toBigDecimal(@Nullable Money amount) {
    return amount == null ? null : amount.value();
  }

  /**
   * No awardee is a null operador, and nothing else. An operador the database has not assigned an
   * identity to cannot be referenced, and storing null for it would record the contract as having
   * no awardee at all — indistinguishable from an award whose fiscal identifier was unusable, and
   * silent. It is a caller's mistake rather than a published value, so it is refused.
   */
  private static @Nullable UUID toOperadorUuid(@Nullable OperadorEconomico operadorEconomico) {
    if (operadorEconomico == null) {
      return null;
    }
    OperadorId operadorId = operadorEconomico.id();
    if (operadorId == null) {
      throw new IllegalArgumentException(
          "operadorEconomico must be stored before the contract awarded to it: %s"
              .formatted(operadorEconomico.fiscalId()));
    }
    return operadorId.value();
  }
}
