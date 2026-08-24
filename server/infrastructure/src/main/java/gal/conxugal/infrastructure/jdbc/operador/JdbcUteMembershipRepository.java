package gal.conxugal.infrastructure.jdbc.operador;

import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/**
 * Stores which firms made up a consortium. The two <strong>reads</strong> derive — a composite key
 * is something Micronaut Data models first-class, and {@code NomeAlternativo} is the shipped
 * precedent for it — while the <strong>write</strong> is hand-written, because the
 * {@code ON CONFLICT} that absorbs a member the source lists twice is not something a derived
 * method expresses. {@link JdbcOperadorRepository} splits the same pair for the same reason.
 *
 * <p>It answers nothing. Every other write in this package hands back the identity the database
 * assigned; here the pair the statement carries <em>is</em> the identity, so there is no
 * {@code RETURNING} clause to read.
 *
 * <p>{@code withdrawn} is the one column the conflict clause refreshes, the other two being the
 * key. That is the column the reconciliation writes to keep a membership's visibility in step with
 * the bids that publish it, and this statement is its only path to it — so a source that publishes
 * a withdrawn membership again un-withdraws it, which is what publishing it again means.
 *
 * <p>The identity {@link GenericRepository} is parameterised with is <strong>half of one</strong>,
 * and it stays inert only because nothing here is keyed by id: this entity's identity is the pair,
 * which no single type names. Widening past {@code GenericRepository} — a {@code CrudRepository},
 * a {@code findById} — would resolve identity against one column of two, so don't.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcUteMembershipRepository
    implements UteMembershipRepository, GenericRepository<UteMembership, OperadorId> {

  private static final String UPSERT =
      """
      INSERT INTO operador_ute_membership (ute_id, operador_economico_id, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::boolean)
      ON CONFLICT ON CONSTRAINT operador_ute_membership_pkey DO UPDATE SET
          withdrawn = EXCLUDED.withdrawn
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcUteMembershipRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public void upsert(UteMembership membership) {
    Objects.requireNonNull(membership, "membership must not be null");
    jdbcOperations.prepareStatement(UPSERT, statement -> {
      statement.setObject(1, membership.uteId().value());
      statement.setObject(2, membership.operadorId().value());
      statement.setBoolean(3, membership.withdrawn());
      return statement.executeUpdate();
    });
  }

  @Override
  public abstract List<UteMembership> findByOperadorIdAndWithdrawnFalse(OperadorId operadorId);

  @Override
  public abstract List<UteMembership> findByUteIdAndWithdrawnFalse(OperadorId uteId);
}
