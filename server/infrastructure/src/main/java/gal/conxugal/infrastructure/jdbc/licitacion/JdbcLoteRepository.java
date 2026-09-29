package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.licitacion.LoteKey;
import gal.conxugal.domain.licitacion.LoteRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a procedure's lotes. One statement, matching on the procedure and the <em>reduced</em>
 * form of the lote's identifier while storing the published one beside it.
 *
 * <p><strong>Writing {@code lote_key} is this adapter's job rather than the column's.</strong> The
 * reduction is {@link LoteKey}'s, measured over 240 procedures and shared with the parse and every
 * join that compares a lote cell; a generated column would restate the same rule in SQL, where the
 * two spellings of it could drift apart without anything noticing. So the rule stays in one
 * language and this statement carries its answer.
 *
 * <p><strong>{@code lote_identifier} is not refreshed, and that is a decision rather than an
 * omission.</strong> The first spelling this store saw is the one it keeps. Refreshing it would
 * make the spelling a reader is shown depend on the order its caller happened to upsert in: the
 * lotes table and the award table of one record publish the same lote differently — {@code 05} and
 * {@code 5} were both measured — so neither is the more published one, and last-write-wins would
 * flip a displayed identifier back and forth between re-imports for no reason a reader could see. A
 * stale spelling is worse than nothing only if it is wrong, and both of these are what the source
 * printed. If a genuine respelling at the source ever has to propagate, that is a rule for the
 * reconciliation to state, not a side effect of statement order.
 *
 * <p>The decoration beside it — the description and the estimated value — <em>is</em> refreshed, on
 * the ordinary rule that a restatement's published attributes are written over. The identifier is
 * not one of them: it is the published form of what this row is keyed on, which is why
 * {@code licitacion}'s own upsert leaves {@code publication_id} out of its refresh too.
 *
 * <p><strong>It must stay a {@link GenericRepository}, which declares no methods.</strong>
 * {@code lote_key} is the one column in this feature that no record component maps, which is safe
 * only while every write here is hand-written. Widening this interface to {@code CrudRepository},
 * or adding a derived {@code save}/{@code update}, would have Micronaut Data build its statement
 * from {@link Lote}'s components alone — omitting {@code lote_key} — and every write would fail on
 * a {@code NOT NULL} violation naming the column rather than the change that caused it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLoteRepository
    implements LoteRepository, GenericRepository<Lote, LoteId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_lote (
          licitacion_id, lote_identifier, lote_key, description, estimated_value, withdrawn)
      VALUES (?::uuid, ?::text, ?::text, ?::text, ?::numeric, ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_lote_key DO UPDATE SET
          description = EXCLUDED.description,
          estimated_value = EXCLUDED.estimated_value,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id, lote_identifier
      """;

  private static final String WITHDRAW_ABSENT = Withdrawals.statementFor("licitacion_lote");

  private final JdbcOperations jdbcOperations;

  protected JdbcLoteRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Lote upsert(Lote lote) {
    Objects.requireNonNull(lote, "lote must not be null");
    String loteKey = keyOf(lote);
    return Upserts.returning(
        jdbcOperations,
        UPSERT,
        statement -> {
          statement.setObject(1, lote.licitacionId().value());
          statement.setString(2, lote.identifier());
          statement.setString(3, loteKey);
          statement.setString(4, lote.description());
          statement.setObject(5, Upserts.amount(lote.estimatedValue()));
          statement.setBoolean(6, lote.withdrawn());
        },
        // The identifier comes back rather than being echoed, because the statement declines to
        // refresh it: for a lote already stored the two differ, and the stored one is the answer.
        rows ->
            new Lote(
                new LoteId(rows.getObject("id", UUID.class)),
                lote.licitacionId(),
                rows.getString("lote_identifier"),
                lote.description(),
                lote.estimatedValue(),
                lote.withdrawn()));
  }

  @Override
  @Transactional
  public int withdrawAbsent(LicitacionId licitacionId, Collection<LoteId> retained) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(retained, "retained must not be null");
    return Withdrawals.absent(
        jdbcOperations, WITHDRAW_ABSENT, licitacionId, retained, LoteId::value);
  }

  /**
   * A lote whose identifier reduces to nothing is not a lote: {@code _} and {@code -} are how the
   * source's tables spell <em>the procedure as a whole</em>, and a row standing for that hangs off
   * the procedure with no lote at all. Refused here rather than stored under a key that would
   * collide with every other such mistake on the same procedure.
   */
  private static String keyOf(Lote lote) {
    return LoteKey.normalise(lote.identifier())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "%s names the procedure as a whole rather than a lote, so it stores no lote row"
                        .formatted(lote.identifier())));
  }
}
