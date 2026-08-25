package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.ResolveOperador;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Stores a procedure's published bidders together with the operadores they name: every single-firm
 * bidder is resolved from the identifier its row published and written as a participation holding
 * a reference to that operador and <strong>no name of its own</strong>.
 *
 * <p><strong>One transaction.</strong> An operador a bid catalogues is created beside the
 * participation that justified it, so a rollback cannot leave a catalogue entry no stored bid
 * points at. {@link ResolveOperador} owns no boundary of its own — that is deliberate, and it makes
 * supplying one each caller's job.
 *
 * <p><strong>A bid contributes no rank</strong>, so this calls
 * {@link ResolveOperador#resolveWithoutRanking}. SPEC-0006 R4 selects the displayed name from the
 * operador's most recently published <em>contract</em>, R15 retains what its contracts published,
 * and R16 defines a participation as a relation to a contract the operador was <em>not</em>
 * awarded. A bid is none of the three, so it neither promotes a name nor enters the retained set —
 * and a rank engineered to lose would do the second of those silently. What a bid does do is
 * <em>create</em> an operador nothing named before, because R16 needs the participation to exist
 * and R3 needs its identifier to resolve to something.
 *
 * <p><strong>A consortium row is routed past this entirely.</strong> Its identifier may come from
 * the procedure's formalisation rather than from its bidder row, so deciding here whether it is
 * identified would decide on half the evidence; cataloguing it and writing its bid belong together
 * and belong to the task that reads both tables. Nothing here is called with a consortium's
 * {@code NIF} cell, which is also why no placeholder can reach the catalogue through this path.
 *
 * <p><strong>Nothing here marks a bid as won.</strong> Which bid won is the award table's answer,
 * not the bidder table's, so every row is written unwon and the resolution that knows better writes
 * over it.
 */
@Singleton
public class StoreLicitacionBidders {

  private final ResolveOperador resolveOperador;
  private final ParticipationRepository participations;

  public StoreLicitacionBidders(
      ResolveOperador resolveOperador, ParticipationRepository participations) {
    this.resolveOperador = resolveOperador;
    this.participations = participations;
  }

  /**
   * Resolves and stores every single-firm bidder the record published, answering the stored
   * participations in the order the source named them.
   *
   * @param licitacionId the stored procedure these bids were made for
   * @param lotes the procedure's stored lotes, which is what a bidder row's lote cell is matched
   *     against
   * @param bidders the bidder table's rows as the source published them, consortia included
   */
  @Transactional
  public List<Participation> store(
      LicitacionId licitacionId, List<Lote> lotes, List<PublishedBidder> bidders) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(lotes, "lotes must not be null");
    Objects.requireNonNull(bidders, "bidders must not be null");
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    List<Participation> stored = new ArrayList<>(bidders.size());
    for (PublishedBidder bidder : bidders) {
      if (bidder instanceof PublishedBidder.SingleFirm firm) {
        stored.add(participations.upsert(bidOf(firm, awardPoints)));
      }
    }
    return List.copyOf(stored);
  }

  /**
   * One published bidder row as the bid this procedure stores, party resolved. A row whose
   * identifier was unusable resolves to nobody and is stored naming nobody: the bid is a fact the
   * source published, and dropping it would lose a bidder R16 requires to be shown.
   */
  private Participation bidOf(PublishedBidder.SingleFirm firm, AwardPoints awardPoints) {
    return new Participation(
        awardPoints.licitacionId(), awardPoints.at(firm.loteKey()), partyOf(firm), false);
  }

  /**
   * The operador this bid names, or null where its published identifier was unusable. Because a
   * participation holds no name, such a bid is recorded as the participation of no catalogue entry
   * rather than as a name with nothing behind it.
   */
  private @Nullable OperadorId partyOf(PublishedBidder.SingleFirm firm) {
    Optional<OperadorEconomico> party =
        resolveOperador.resolveWithoutRanking(firm.fiscalIdentifier(), firm.name());
    if (party.isEmpty()) {
      return null;
    }
    return Objects.requireNonNull(
        party.get().id(), "a catalogued operador must carry an identity");
  }

  /**
   * The points a procedure was bid at: its lotes by the one form everything compares a lote cell
   * on, and the procedure itself for a row that names no lote.
   *
   * <p>The stored spelling is reduced here rather than trusted to match a bidder row's, because the
   * two source tables spell one lote differently — {@code 05} against {@code 5} was measured within
   * a single record.
   */
  private record AwardPoints(LicitacionId licitacionId, Map<String, LoteId> byKey) {

    AwardPoints {
      byKey = Map.copyOf(byKey);
    }

    static AwardPoints of(LicitacionId licitacionId, Iterable<Lote> lotes) {
      Map<String, LoteId> byKey = new HashMap<>();
      for (Lote lote : lotes) {
        LoteId id =
            Objects.requireNonNull(
                lote.id(), "a stored lote must carry an identity: " + lote.identifier());
        LoteKey.normalise(lote.identifier()).ifPresent(key -> byKey.put(key, id));
      }
      return new AwardPoints(licitacionId, byKey);
    }

    /**
     * The point {@code loteKey} names: a lote, or the procedure as a whole where the row named no
     * lote.
     *
     * <p>A row naming a lote the procedure has not stored fails the procedure rather than being
     * filed somewhere plausible: keying it to the procedure would collide with every other such bid
     * on the same natural key, and skipping it would lose a published bidder silently. The record
     * parse takes the same view of a bidder count that disagrees with the award table's.
     */
    @Nullable LoteId at(@Nullable String loteKey) {
      if (loteKey == null) {
        return null;
      }
      LoteId awardPoint = byKey.get(loteKey);
      if (awardPoint == null) {
        throw new IllegalArgumentException(
            "bidder names lote %s, which procedure %s has not stored"
                .formatted(loteKey, licitacionId.value()));
      }
      return awardPoint;
    }
  }
}
