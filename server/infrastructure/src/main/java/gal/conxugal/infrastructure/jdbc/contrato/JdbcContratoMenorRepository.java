package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorId;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.UpsertCounts;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganosWithVisibleContracts;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcContratoMenorRepository
    implements ContratoMenorRepository,
        OrganosWithVisibleContracts,
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
