package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateId;
import gal.conxugal.domain.licitacion.LicitacionStateRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * Stores the published state vocabulary. One statement, matching on the code — the source's own
 * identifier for a state — and never on the label: 101 and 102 are both published as
 * <em>Histórico</em>, so a store keyed on the label would hold one row where the source publishes
 * two states.
 *
 * <p>The label <em>is</em> refreshed, because it is the one thing about a state that can be
 * corrected at the source, and a code is not a thing the source restates.
 *
 * <p>It runs in the caller's transaction rather than one of its own, and that is the whole point of
 * the port's shape: a code the table has never held creates its row inside the very transaction
 * that stores the procedure naming it, so an unseen code costs a row rather than a rejected
 * procedure.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionStateRepository
    implements LicitacionStateRepository, GenericRepository<LicitacionState, LicitacionStateId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_state (code, label) VALUES (?::int, ?::text)
      ON CONFLICT ON CONSTRAINT licitacion_state_code_key DO UPDATE SET label = EXCLUDED.label
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLicitacionStateRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public LicitacionState upsert(LicitacionState state) {
    Objects.requireNonNull(state, "state must not be null");
    return new LicitacionState(
        new LicitacionStateId(
            Upserts.returningId(
                jdbcOperations,
                UPSERT,
                statement -> {
                  statement.setInt(1, state.code());
                  statement.setString(2, state.label());
                })),
        state.code(),
        state.label());
  }
}
