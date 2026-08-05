package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorId;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.UpsertCounts;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>{@code operador_economico_id} is written on insert and absent from the update <em>because
 * nothing derives an awardee yet</em>, so a re-import carries none and the update has nothing
 * truthful to write there. This is a consequence of the ordering, not a rule: the derivation task
 * resolves the awardee on every upsert precisely so that a corrected fiscal identifier repoints
 * the foreign key, and adding {@code operador_economico_id = EXCLUDED.operador_economico_id} to
 * the update is that task's to make.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcContratoMenorRepository
    implements ContratoMenorRepository, GenericRepository<ContratoMenor, ContratoMenorId> {

  private static final String UPSERT_SQL =
      """
      INSERT INTO contrato_menor (
          source_id, organo_id, publication_date, obxecto, amount, duration,
          operador_economico_id)
      SELECT * FROM unnest(
          ?::bigint[], ?::uuid[], ?::date[], ?::text[], ?::numeric[], ?::varchar[], ?::uuid[])
      ON CONFLICT (source_id) DO UPDATE SET
          organo_id = EXCLUDED.organo_id,
          publication_date = EXCLUDED.publication_date,
          obxecto = EXCLUDED.obxecto,
          amount = EXCLUDED.amount,
          duration = EXCLUDED.duration
      RETURNING (xmax = 0) AS inserted
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
    statement.setArray(6, connection.createArrayOf("varchar", durations));
    statement.setArray(7, connection.createArrayOf("uuid", operadorIds));
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
