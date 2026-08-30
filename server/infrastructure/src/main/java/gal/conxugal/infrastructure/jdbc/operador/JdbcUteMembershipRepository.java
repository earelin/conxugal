package gal.conxugal.infrastructure.jdbc.operador;

import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
 * <p>{@code withdrawn} is the one column the conflict clause refreshes, the other three being the
 * key. A source that publishes a withdrawn membership again therefore un-withdraws it, which is
 * what publishing it again means. Setting the marker is {@link #withdrawAbsent}'s, which is the
 * only other statement here allowed to touch the column.
 *
 * <p>The identity {@link GenericRepository} is parameterised with is <strong>a third of
 * one</strong>, and it stays inert only because nothing here is keyed by id: this entity's identity
 * is the triple, which no single type names. Widening past {@code GenericRepository} — a
 * {@code CrudRepository}, a {@code findById} — would resolve identity against one column of three,
 * so don't.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcUteMembershipRepository
    implements UteMembershipRepository, GenericRepository<UteMembership, OperadorId> {

  private static final String UPSERT =
      """
      INSERT INTO operador_ute_membership (
          ute_id, operador_economico_id, licitacion_id, withdrawn)
      VALUES (?::uuid, ?::uuid, ?::uuid, ?::boolean)
      ON CONFLICT ON CONSTRAINT operador_ute_membership_pkey DO UPDATE SET
          withdrawn = EXCLUDED.withdrawn
      """;

  /**
   * Every statement this procedure makes that the retained set does not, marked withdrawn.
   *
   * <p><strong>The retained set is pairs, so it is joined rather than tested against.</strong> The
   * table carries no surrogate identity — the triple is what it keys on — so the two halves arrive
   * as parallel arrays and {@code unnest} puts them back together. Empty arrays yield no rows, the
   * {@code NOT EXISTS} holds for everything, and the procedure's whole membership goes: the
   * consortium the record stopped publishing.
   *
   * <p>{@code NOT withdrawn} keeps a re-import that changes nothing from rewriting rows it already
   * marked, which is what makes the statement's row count mean <em>newly</em> withdrawn.
   */
  private static final String WITHDRAW_ABSENT =
      """
      UPDATE operador_ute_membership AS membership
      SET withdrawn = TRUE
      WHERE membership.licitacion_id = ?::uuid
          AND NOT membership.withdrawn
          AND NOT EXISTS (
              SELECT 1
              FROM unnest(?::uuid[], ?::uuid[]) AS retained(ute_id, operador_economico_id)
              WHERE retained.ute_id = membership.ute_id
                  AND retained.operador_economico_id = membership.operador_economico_id)
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
      statement.setObject(3, membership.licitacionId().value());
      statement.setBoolean(4, membership.withdrawn());
      return statement.executeUpdate();
    });
  }

  @Override
  @Transactional
  public void withdrawAbsent(LicitacionId licitacionId, Collection<UteMembership> retained) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(retained, "retained must not be null");
    UUID[] utes = new UUID[retained.size()];
    UUID[] members = new UUID[retained.size()];
    int index = 0;
    for (UteMembership membership : retained) {
      utes[index] = membership.uteId().value();
      members[index] = membership.operadorId().value();
      index++;
    }
    jdbcOperations.prepareStatement(WITHDRAW_ABSENT, statement -> {
      statement.setObject(1, licitacionId.value());
      statement.setArray(2, statement.getConnection().createArrayOf("uuid", utes));
      statement.setArray(3, statement.getConnection().createArrayOf("uuid", members));
      return statement.executeUpdate();
    });
  }

  @Override
  public abstract List<UteMembership> findByOperadorIdAndWithdrawnFalse(OperadorId operadorId);

  @Override
  public abstract List<UteMembership> findByUteIdAndWithdrawnFalse(OperadorId uteId);
}
