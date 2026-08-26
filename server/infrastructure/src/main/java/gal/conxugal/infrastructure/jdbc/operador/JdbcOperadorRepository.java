package gal.conxugal.infrastructure.jdbc.operador;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 *
 * <p>The same statement <strong>refuses to retain the name the operador is displayed
 * under</strong>. That is the state the aggregate throws on being built in, and nothing here
 * could recover from it: the port offers no delete, so one such row would make the operador
 * unreadable for good, through the very read its own derivation performs on the next contract.
 * The guard is the store's because a caller that forgets it leaves no error behind — the failure
 * mode the unique fiscal identifier is enforced against for the same reason.
 *
 * <p><strong>It makes the order of the two writes matter</strong>, and this is the one thing a
 * caller has to know: when a contract outranks the incumbent, promote first and retain the
 * displaced name afterwards. Retaining first would be asked to file a name that is still the
 * displayed one, and this statement would decline it — costing the name rather than corrupting the
 * row, but costing it silently. Promotion drops the promoted name from the retained set, so that
 * order needs nothing else of the caller.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcOperadorRepository
    implements OperadorRepository, GenericRepository<OperadorEconomico, OperadorId> {

  /**
   * The fold {@link MatchableName} defines, expressed once for both of the tables a name can be
   * held in. It has to answer exactly what that type answers — a key computed there is compared
   * against a name folded here — which is why it is those three steps rather than a table of
   * accented characters: lower-case, decompose, and keep the unaccented ASCII letters and digits.
   * {@code normalize(…, NFD)} splits an accented letter into its base and a combining mark, and the
   * replacement drops the mark along with every comma, full stop and space.
   *
   * <p>No index serves it and none is added on speculation. The catalogue match is the last route
   * an award tries and reaches roughly a third of them; if the scan ever shows in an import's
   * timings, an expression index over this exact expression is what it costs.
   */
  private static final String FOLDED_NAME =
      "regexp_replace(normalize(lower(%s), NFD), '[^a-z0-9]', '', 'g')";

  private static final String OPERADORES_MATCHING_NAME =
      """
      SELECT id FROM operador_economico WHERE %s = ?::text
      UNION
      SELECT operador_economico_id AS id
        FROM operador_economico_nome_alternativo
       WHERE %s = ?::text
      """
          .formatted(FOLDED_NAME.formatted("name"), FOLDED_NAME.formatted("name"));

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
      SELECT ?::uuid, ?::text, ?::date, ?::bigint
       WHERE NOT EXISTS (
           SELECT 1 FROM operador_economico WHERE id = ?::uuid AND name = ?::text)
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

  /**
   * The operadores holding this name, either as the one they are displayed under or as one they
   * have retained. {@code UNION} rather than {@code UNION ALL}, so an operador holding the name
   * both ways — which promotion makes impossible but a read cannot assume — answers once, and so
   * that the count the caller judges ambiguity on counts parties rather than rows.
   */
  @Override
  @Transactional
  public Set<OperadorId> findAllMatchingName(MatchableName name) {
    Objects.requireNonNull(name, "name must not be null");
    Set<OperadorId> matching = new HashSet<>();
    jdbcOperations.prepareStatement(OPERADORES_MATCHING_NAME, statement -> {
      statement.setString(1, name.value());
      statement.setString(2, name.value());
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          matching.add(new OperadorId(rows.getObject("id", UUID.class)));
        }
      }
      return matching;
    });
    return Set.copyOf(matching);
  }

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
      statement.setObject(5, operadorId.value());
      statement.setString(6, nome.name());
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
