package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Participation;
import gal.conxugal.domain.licitacion.ParticipationId;
import gal.conxugal.domain.licitacion.ParticipationRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores a procedure's published bidders. One statement, keyed on the bid — the procedure, the
 * lote and the operador the bidder resolved to. Two of those three are nullable, which is why the
 * key is declared {@code NULLS NOT DISTINCT} and the primary key is the surrogate the statement
 * answers with.
 *
 * <p>Only the two facts outside that key are refreshed. {@code won} is one of them, so a
 * restatement that moves the award between two bidders of one lote is recorded on both rows rather
 * than leaving the loser marked as the winner.
 *
 * <p>{@code withdrawn} is refreshed too, on {@link JdbcAwardRepository}'s reasoning: the
 * reconciliation that marks a bidder withdrawn writes it through this statement and has no other
 * path, so a source that publishes a withdrawn bidder again un-withdraws it.
 *
 * <p>Nothing here describes the party. A consortium is an operador before its bid is written, so
 * the operador reference is the whole of what this row says about who bid — which is what removed
 * the consortium marker, the published name, and the constraint that bound the two.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcParticipationRepository
    implements ParticipationRepository, GenericRepository<Participation, ParticipationId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_participation (
          licitacion_id, lote_id, operador_economico_id, won, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::uuid, ?::boolean, ?::boolean)
      ON CONFLICT ON CONSTRAINT licitacion_participation_key DO UPDATE SET
          won = EXCLUDED.won,
          withdrawn = EXCLUDED.withdrawn
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcParticipationRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Participation upsert(Participation participation) {
    Objects.requireNonNull(participation, "participation must not be null");
    UUID id =
        Upserts.returningId(
            jdbcOperations,
            UPSERT,
            statement -> {
              statement.setObject(1, participation.licitacionId().value());
              statement.setObject(2, Upserts.lote(participation.loteId()));
              statement.setObject(3, Upserts.operador(participation.operadorEconomicoId()));
              statement.setBoolean(4, participation.won());
              statement.setBoolean(5, participation.withdrawn());
            });
    return new Participation(
        new ParticipationId(id),
        participation.licitacionId(),
        participation.loteId(),
        participation.operadorEconomicoId(),
        participation.won(),
        participation.withdrawn());
  }
}
