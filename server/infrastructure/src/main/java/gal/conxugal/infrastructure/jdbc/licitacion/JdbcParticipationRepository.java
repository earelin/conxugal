package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.Award;
import gal.conxugal.domain.licitacion.LicitacionId;
import gal.conxugal.domain.licitacion.Participation;
import gal.conxugal.domain.licitacion.ParticipationId;
import gal.conxugal.domain.licitacion.ParticipationRepository;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.infrastructure.jdbc.operador.NameFold;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores a procedure's published bidders. The write is one statement, keyed on the bid — the
 * procedure, the lote and the operador the bidder resolved to. Two of those three are nullable,
 * which is why the key is declared {@code NULLS NOT DISTINCT} and the primary key is the surrogate
 * the statement answers with.
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

  private static final String CONSORTIUM_OPERADOR =
      """
      SELECT operador_economico.id
        FROM licitacion_participation
        JOIN operador_economico
          ON operador_economico.id = licitacion_participation.operador_economico_id
       WHERE licitacion_participation.licitacion_id = ?::uuid
         AND operador_economico.ute
         AND %s = ?::text
       ORDER BY operador_economico.fiscal_id IS NULL, operador_economico.id
       LIMIT 1
      """
          .formatted(NameFold.of("operador_economico.name"));

  private static final String WITHDRAW_ABSENT =
      Withdrawals.statementFor("licitacion_participation");

  /**
   * Every bid of the procedure set to whether one of the award points named won it.
   *
   * <p><strong>An update and never an insert.</strong> The awardee of an award need not have bid —
   * the bidder table is absent on most records, and a name-derived awardee need not appear on this
   * procedure at all — so writing the winner through the upsert would invent a participation the
   * source never published.
   *
   * <p><strong>{@code IS NOT DISTINCT FROM} on the lote, not {@code =}.</strong> The award point of
   * a procedure with no lotes is null on both sides, which 85 of 100 measured procedures are, and
   * an equality there would match nothing and mark no winner on almost every procedure in the
   * catalogue. The operador is compared with {@code =} on purpose: two nulls are two parties
   * nothing identified, not one party.
   *
   * <p>Every row is <em>considered</em> rather than only the winners, so a bid that stops winning is
   * cleared by the same statement and empty arrays clear the procedure outright — but only the rows
   * whose marker actually moves are written. {@code IS DISTINCT FROM} against the computed value is
   * what makes a re-import that changes nothing cost no rows here, on the {@code NOT withdrawn}
   * guard's reasoning; the predicate is computed once in the CTE rather than repeated in the filter,
   * so the two can never drift apart.
   */
  private static final String MARK_WINNERS =
      """
      WITH marked AS (
          SELECT bid.id,
              EXISTS (
                  SELECT 1
                  FROM unnest(?::uuid[], ?::uuid[]) AS winner(lote_id, operador_economico_id)
                  WHERE winner.lote_id IS NOT DISTINCT FROM bid.lote_id
                      AND winner.operador_economico_id = bid.operador_economico_id) AS won
          FROM licitacion_participation AS bid
          WHERE bid.licitacion_id = ?::uuid)
      UPDATE licitacion_participation AS bid
      SET won = marked.won
      FROM marked
      WHERE marked.id = bid.id
          AND bid.won IS DISTINCT FROM marked.won
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

  /**
   * The identifier-less consortium this procedure already catalogued under this name, read from the
   * bid that named it. The join runs from the bid to the catalogue rather than the other way round,
   * which is the whole point: the catalogue holds nothing that would find such an entry, and the
   * procedure's bids do.
   *
   * <p>The {@code licitacion_id} filter is served by the leading column of the natural key's index,
   * and a procedure has at most a few dozen bids, so the fold is applied to a handful of rows
   * rather than to the catalogue.
   *
   * <p><strong>{@code withdrawn} is deliberately not in the predicate</strong>, on the port's
   * reasoning: a reconciliation that withdraws the bid and then re-imports the procedure must find
   * the same entry rather than mint a second beside it.
   *
   * <p><strong>An identified entry is preferred over an identifier-less one</strong>, which is what
   * {@code fiscal_id IS NULL} leading the sort buys. The caller only asks when the record itself
   * published no identifier, so both kinds can be present only where an earlier import identified
   * the consortium and this one no longer does; answering with the identified entry keeps the bid
   * on the operador the award still names.
   *
   * <p><strong>The rest of the order is explicit under the {@code LIMIT}</strong>, though the
   * invariant says there is at most one row of each kind to order. Nothing in the schema enforces
   * that invariant — it rests on this lookup and on one import running at a time — so the day it
   * does break, an unordered limit would pick a different duplicate on different runs and leave two
   * imports disagreeing about which operador the procedure's consortium is. Ordered, the degraded
   * case is at least stable and diagnosable.
   */
  @Override
  @Transactional
  public Optional<OperadorId> findConsortiumOperador(
      LicitacionId licitacionId, MatchableName name) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(name, "name must not be null");
    return jdbcOperations.prepareStatement(CONSORTIUM_OPERADOR, statement -> {
      statement.setObject(1, licitacionId.value());
      statement.setString(2, name.value());
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next()
            ? Optional.of(new OperadorId(rows.getObject("id", UUID.class)))
            : Optional.<OperadorId>empty();
      }
    });
  }

  @Override
  @Transactional
  public int withdrawAbsent(LicitacionId licitacionId, Collection<ParticipationId> retained) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(retained, "retained must not be null");
    return Withdrawals.absent(
        jdbcOperations, WITHDRAW_ABSENT, licitacionId, retained, ParticipationId::value);
  }

  @Override
  @Transactional
  public void markWinners(LicitacionId licitacionId, Collection<Award> awards) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(awards, "awards must not be null");
    List<Award> won = awards.stream().filter(JdbcParticipationRepository::marksWinner).toList();
    UUID[] lotes = new UUID[won.size()];
    UUID[] operadores = new UUID[won.size()];
    for (int index = 0; index < won.size(); index++) {
      Award award = won.get(index);
      lotes[index] = Upserts.lote(award.loteId());
      operadores[index] = Upserts.operador(award.operadorEconomicoId());
    }
    jdbcOperations.prepareStatement(MARK_WINNERS, statement -> {
      statement.setArray(1, statement.getConnection().createArrayOf("uuid", lotes));
      statement.setArray(2, statement.getConnection().createArrayOf("uuid", operadores));
      statement.setObject(3, licitacionId.value());
      return statement.executeUpdate();
    });
  }

  /**
   * Whether this award says a bid won: it has to name somebody, and it has to still be an award
   * this procedure makes. A withdrawn one is neither, and an award naming nobody would compare a
   * null against every bid that also resolved to nobody.
   */
  private static boolean marksWinner(Award award) {
    return !award.withdrawn() && award.operadorEconomicoId() != null;
  }
}
