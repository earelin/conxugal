package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvId;
import gal.conxugal.domain.licitacion.CpvRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores CPV entries. One statement, matching on the code the list assigns.
 *
 * <p>The conflict branch <strong>writes the stored description back over itself</strong> rather
 * than taking the incoming one, which is what makes this upsert unable to blank wording something
 * else supplied. Every import this feature builds carries none — the record's CPV table publishes
 * the code alone — so the alternative would empty a description on the next run over a procedure
 * citing it. That is why the answer carries the description read back rather than the one passed
 * in: for an entry already held, the two differ.
 *
 * <p>It is a {@code DO UPDATE} rather than a {@code DO NOTHING} because the port answers the stored
 * entry even when nothing changed, and {@code DO NOTHING} returns no row at all. The obvious
 * work-around — read back whatever the insert declined to write — is not equivalent under
 * concurrency: the losing writer's snapshot predates the winner's commit, so it would find nothing
 * either.
 *
 * <p>The conflict target is named rather than inferred, so the statement fails at deployment if the
 * migration's constraint is ever renamed instead of silently matching a different one.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcCpvRepository implements CpvRepository, GenericRepository<Cpv, CpvId> {

  private static final String UPSERT =
      """
      INSERT INTO cpv (code, description) VALUES (?::text, ?::text)
      ON CONFLICT ON CONSTRAINT cpv_code_key DO UPDATE SET description = cpv.description
      RETURNING id, description
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcCpvRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Cpv upsert(Cpv cpv) {
    Objects.requireNonNull(cpv, "cpv must not be null");
    return Upserts.returning(
        jdbcOperations,
        UPSERT,
        statement -> {
          statement.setString(1, cpv.code());
          statement.setString(2, cpv.description());
        },
        rows ->
            new Cpv(
                new CpvId(rows.getObject("id", UUID.class)),
                cpv.code(),
                rows.getString("description")));
  }
}
