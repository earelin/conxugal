package gal.conxugal.infrastructure.jdbc.operador;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeAlternativo;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Stores the operadores económicos catalogue. {@code findByFiscalId} and {@code insert} derive
 * from the port; the two writes below cannot — {@code retain} is not a Micronaut Data prefix and
 * promoting a name spans two tables — so they are written out.
 *
 * <p>Promoting is <strong>one transaction over two statements</strong>: the row takes the name and
 * the rank together, and the retained copy of that name goes. A promotion that left the row behind
 * would write an operador holding its own displayed name as an alternative, which the aggregate
 * refuses to be built in — so no later read could load it.
 *
 * <p>Retaining is <strong>one statement</strong>, an upsert on the composite key, so a name already
 * held is advanced rather than rejected and no caller reads first and races. It advances
 * <strong>only when the incoming rank wins</strong>, which is what makes each retained name carry
 * the most recent contract that published it rather than the last one to arrive. The comparison
 * coalesces an absent date to {@code -infinity} so that it mirrors {@link NomeRank}: an undated
 * rank loses to every dated one and still orders against another undated one by source identifier.
 * Comparing the columns directly would answer {@code NULL} whenever either side is undated, and a
 * {@code NULL} condition skips the update silently.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcOperadorRepository
    implements OperadorRepository, GenericRepository<OperadorEconomico, OperadorId> {

  private static final String PROMOTE_NAME =
      """
      UPDATE operador_economico
         SET name = ?, name_rank_date = ?, name_rank_source_id = ?
       WHERE id = ?
      """;

  private static final String STOP_RETAINING_NAME =
      """
      DELETE FROM operador_economico_nome_alternativo
       WHERE operador_economico_id = ? AND name = ?
      """;

  private static final String RETAIN_NAME =
      """
      INSERT INTO operador_economico_nome_alternativo (
          operador_economico_id, name, last_published_date, last_published_source_id)
      VALUES (?, ?, ?, ?)
      ON CONFLICT (operador_economico_id, name) DO UPDATE SET
          last_published_date = EXCLUDED.last_published_date,
          last_published_source_id = EXCLUDED.last_published_source_id
       WHERE (COALESCE(EXCLUDED.last_published_date, '-infinity'::date),
              EXCLUDED.last_published_source_id)
           > (COALESCE(operador_economico_nome_alternativo.last_published_date, '-infinity'::date),
              operador_economico_nome_alternativo.last_published_source_id)
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcOperadorRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  /**
   * Declared only to carry the join. Without it the retained names are fetched by no query at all
   * and the operador comes back holding an empty set, which reads as an operador that has borne
   * one name rather than as one whose names were not asked for.
   */
  @Override
  @Join(value = "nomesAlternativos", type = Join.Type.LEFT_FETCH)
  public abstract Optional<OperadorEconomico> findByFiscalId(FiscalIdentifier fiscalId);

  @Override
  @Transactional
  public void promoteName(OperadorId id, String name, NomeRank nameRank) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(nameRank, "nameRank must not be null");
    executeUpdate(PROMOTE_NAME, statement -> {
      statement.setString(1, name);
      statement.setObject(2, toSqlDate(nameRank.date()));
      statement.setLong(3, nameRank.sourceId());
      statement.setObject(4, id.value());
    });
    executeUpdate(STOP_RETAINING_NAME, statement -> {
      statement.setObject(1, id.value());
      statement.setString(2, name);
    });
  }

  @Override
  @Transactional
  public void retainName(NomeAlternativo nome) {
    Objects.requireNonNull(nome, "nome must not be null");
    OperadorId operadorId = nome.operadorEconomicoId();
    if (operadorId == null) {
      throw new IllegalArgumentException(
          "the operador must be stored before a name is retained beside it: %s".formatted(
              nome.name()));
    }
    NomeRank lastPublished = nome.lastPublished();
    executeUpdate(RETAIN_NAME, statement -> {
      statement.setObject(1, operadorId.value());
      statement.setString(2, nome.name());
      statement.setObject(3, toSqlDate(lastPublished.date()));
      statement.setLong(4, lastPublished.sourceId());
    });
  }

  private void executeUpdate(String sql, Binding binding) {
    jdbcOperations.prepareStatement(sql, statement -> {
      binding.bind(statement);
      return statement.executeUpdate();
    });
  }

  private static @Nullable Date toSqlDate(@Nullable LocalDate date) {
    return date == null ? null : Date.valueOf(date);
  }

  @FunctionalInterface
  private interface Binding {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
