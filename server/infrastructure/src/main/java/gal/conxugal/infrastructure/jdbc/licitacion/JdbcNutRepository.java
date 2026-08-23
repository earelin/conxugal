package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutId;
import gal.conxugal.domain.licitacion.NutRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores NUTS entries, on {@link JdbcCpvRepository}'s shape and for its reasons — including the
 * conflict branch that writes the stored description back over itself rather than taking the
 * incoming one.
 *
 * @see JdbcCpvRepository
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcNutRepository implements NutRepository, GenericRepository<Nut, NutId> {

  private static final String UPSERT =
      """
      INSERT INTO nut (code, description) VALUES (?::text, ?::text)
      ON CONFLICT ON CONSTRAINT nut_code_key DO UPDATE SET description = nut.description
      RETURNING id, description
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcNutRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public Nut upsert(Nut nut) {
    Objects.requireNonNull(nut, "nut must not be null");
    return Upserts.returning(
        jdbcOperations,
        UPSERT,
        statement -> {
          statement.setString(1, nut.code());
          statement.setString(2, nut.description());
        },
        rows ->
            new Nut(
                new NutId(rows.getObject("id", UUID.class)),
                nut.code(),
                rows.getString("description")));
  }
}
