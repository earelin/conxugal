package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Lote;
import gal.conxugal.domain.licitacion.LoteId;
import gal.conxugal.domain.licitacion.LoteKey;
import gal.conxugal.domain.licitacion.LoteRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
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
 * <p>The conflict branch refreshes {@code lote_identifier} as well, so a source that respells the
 * padding between imports moves the published spelling on the row it already has rather than
 * gaining a twin.
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
          lote_identifier = EXCLUDED.lote_identifier,
          description = EXCLUDED.description,
          estimated_value = EXCLUDED.estimated_value,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLoteRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Lote upsert(Lote lote) {
    Objects.requireNonNull(lote, "lote must not be null");
    String loteKey = keyOf(lote);
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, lote.licitacionId().value());
              statement.setString(2, lote.identifier());
              statement.setString(3, loteKey);
              statement.setString(4, lote.description());
              statement.setObject(5, Upserts.amount(lote.estimatedValue()));
              statement.setBoolean(6, lote.withdrawn());
            });
    return new Lote(
        new LoteId(id),
        lote.licitacionId(),
        lote.identifier(),
        lote.description(),
        lote.estimatedValue(),
        lote.withdrawn());
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
