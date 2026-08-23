package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Award;
import gal.conxugal.domain.licitacion.AwardId;
import gal.conxugal.domain.licitacion.AwardRepository;
import gal.conxugal.domain.operador.OperadorId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Stores a procedure's awards. One statement, keyed on the award point — the procedure and the
 * lote, where a null lote is the procedure as a whole and 85 of 100 measured procedures have no
 * lotes at all.
 *
 * <p>{@code operador_economico_id} is refreshed like every other published value, which is what
 * makes a restatement that changes who was awarded repoint the row rather than leave it naming a
 * party the source no longer does. So is {@code awardee_resolution_path}: the two are one fact, and
 * refreshing the link while leaving the path behind would record an award as resolved by a route
 * that no longer applies to it.
 *
 * <p>{@code withdrawn} <strong>is</strong> refreshed here, unlike on the procedure itself. The
 * reconciliation that marks a child withdrawn writes it through this statement — there is no other
 * write path — so leaving it out would leave that reconciliation no way to set it. The consequence
 * is worth stating: a source that publishes a withdrawn award again un-withdraws it, which is what
 * publishing it again means.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcAwardRepository
    implements AwardRepository, GenericRepository<Award, AwardId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_award (
          licitacion_id, lote_id, resolution, resolution_date, amount, execution_period,
          awardee_name, operador_economico_id, awardee_resolution_path, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::text, ?::date, ?::numeric, ?::text, ?::text, ?::uuid, ?::text,
              ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_award_key DO UPDATE SET
          resolution = EXCLUDED.resolution,
          resolution_date = EXCLUDED.resolution_date,
          amount = EXCLUDED.amount,
          execution_period = EXCLUDED.execution_period,
          awardee_name = EXCLUDED.awardee_name,
          operador_economico_id = EXCLUDED.operador_economico_id,
          awardee_resolution_path = EXCLUDED.awardee_resolution_path,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcAwardRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Award upsert(Award award) {
    Objects.requireNonNull(award, "award must not be null");
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, award.licitacionId().value());
              statement.setObject(2, Upserts.lote(award.loteId()));
              statement.setString(3, award.resolution());
              statement.setObject(4, Upserts.date(award.resolutionDate()));
              statement.setObject(5, Upserts.amount(award.amount()));
              statement.setString(6, award.executionPeriod());
              statement.setString(7, award.awardeeName());
              statement.setObject(8, operador(award.operadorEconomicoId()));
              statement.setString(9, award.awardeeResolutionPath().name());
              statement.setBoolean(10, award.withdrawn());
            });
    return new Award(
        new AwardId(id),
        award.licitacionId(),
        award.loteId(),
        award.resolution(),
        award.resolutionDate(),
        award.amount(),
        award.executionPeriod(),
        award.awardeeName(),
        award.operadorEconomicoId(),
        award.awardeeResolutionPath(),
        award.withdrawn());
  }

  /**
   * No awardee is a null operador and nothing else: an award that names nobody is a supported
   * outcome here rather than a failure, and the resolution path beside it says why.
   */
  private static @Nullable UUID operador(@Nullable OperadorId operadorId) {
    return operadorId == null ? null : operadorId.value();
  }
}
