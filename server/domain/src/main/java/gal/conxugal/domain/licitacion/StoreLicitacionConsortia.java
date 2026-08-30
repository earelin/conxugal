package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.licitacion.ConsortiumOperadores.CataloguedConsortium;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.ResolveOperador;
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Catalogues each consortium a procedure published, writes its bids, and relates it to the firms
 * it was made of.
 *
 * <p><strong>A UTE is an operador either way</strong>, and the source's reticence changes one
 * thing only. Where it publishes a fiscal identifier for the consortium the entry holds it and is
 * the same operador on every other procedure naming it; where it does not — 33 of every 35
 * measured rows — the entry holds none and is keyed on the bid that published it. Its members are
 * operadores under SPEC-0006 R3 in both branches, the membership is an operador-to-operador
 * relation in both, and any award it won is held by the consortium's own operador and enters no
 * member's totals.
 *
 * <p><strong>The identifier is resolved before the operador is created, and that ordering is the
 * design rather than an optimisation.</strong> A formalisation often publishes an identifier the
 * bidder row did not, so <em>identified</em> is a property of the procedure and not of the bidder
 * row. Creating the bid's operador first and identifying it afterwards would mint an
 * identifier-less UTE that the formalisation then has to merge into the identified one — a
 * retro-active re-partition of a row already written, which ADR-0023 rests on never having to
 * perform — and, until it did, the bid would point at one operador while the award pointed at
 * another, leaving the identified one holding an award and no members.
 *
 * <p><strong>This runs before {@link StoreLicitacionAwards}</strong>, whose routing takes the
 * answer rather than deriving it again, and beside {@link StoreLicitacionBidders}, which stores
 * the single-firm rows of the same table and routes every consortium here.
 *
 * <p><strong>One consortium per procedure and published name, not per bidder row.</strong> A UTE
 * is constituted for a procedure, so a consortium bidding on two of its lotes is one catalogue
 * entry with two bids and one membership — not two entries splitting its members and its awards
 * between them. <strong>What that costs is two consortia of one procedure whose names fold
 * alike</strong>: they become one entry holding the union of their members. The source would have
 * to publish two genuinely different consortia under names differing only in case, accent or
 * punctuation, and the alternative — keying on the raw spelling — mints a second entry every time
 * a restatement repunctuates one, which is the commoner failure by far.
 *
 * <p><strong>It is minted once and found again by the bid it made.</strong> An identifier-less
 * entry answers to nothing inside the catalogue, so a re-import would mint a second without
 * {@link ParticipationRepository#findConsortiumOperador}, which reaches it through the
 * participation this procedure already stored. That lookup is scoped to one procedure, which is
 * what keeps it clear of the continuity SPEC-0006 R3 refuses to claim: two procedures publishing a
 * consortium under one name are two operadores, deliberately.
 *
 * <p><strong>One transaction, and one import.</strong> The operadores a consortium catalogues are
 * created beside the bid and the memberships that justified them, so a rollback cannot leave a
 * catalogue entry no stored bid points at; {@link ResolveOperador} owns no boundary of its own.
 * Two imports of one procedure running at once would each mint a consortium, since nothing in the
 * schema refuses the second — that is held off by the system-wide one-import-at-a-time guard
 * ADR-0023 already rests the catalogue's create-if-absent path on, and it is named here because
 * this is a second thing depending on it rather than a new assumption.
 */
@Singleton
public class StoreLicitacionConsortia {

  private final ResolveOperador resolveOperador;
  private final ParticipationRepository participations;
  private final UteMembershipRepository memberships;

  public StoreLicitacionConsortia(
      ResolveOperador resolveOperador,
      ParticipationRepository participations,
      UteMembershipRepository memberships) {
    this.resolveOperador = resolveOperador;
    this.participations = participations;
    this.memberships = memberships;
  }

  /**
   * Catalogues every consortium the record published, stores its bids and its membership, withdraws
   * the memberships this procedure no longer states, and answers which operador each became — which
   * is what the award resolution then attributes to.
   *
   * <p><strong>The membership reconciliation is this class's and not its caller's</strong>, because
   * this is the only place a procedure's whole published membership is known: above it there are
   * bidder rows, and below it there are operadores, and the mapping between them is what happens
   * here. It is scoped to this procedure, so a UTE the source identifies keeps every membership
   * another procedure still states — see {@link UteMembershipRepository#withdrawAbsent}.
   *
   * <p>The bids, by contrast, are handed <em>back</em>: they are half of one table that
   * {@link StoreLicitacionBidders} writes the other half of, and reconciling half a table against
   * itself would withdraw every single-firm bid on the procedure.
   *
   * @param licitacionId the stored procedure these bids were made for
   * @param lotes the procedure's stored lotes, which is what a bidder row's lote cell is matched
   *     against
   * @param bidders the bidder table's rows as the source published them, single firms included
   * @param formalisations the formalisation table's rows, the second place a consortium's own
   *     identifier is published and often the only one
   */
  @Transactional
  public StoredConsortia store(
      LicitacionId licitacionId,
      List<Lote> lotes,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(lotes, "lotes must not be null");
    Objects.requireNonNull(bidders, "bidders must not be null");
    Objects.requireNonNull(formalisations, "formalisations must not be null");
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    ConsortiumRows rows = consortiumRowsOf(bidders);
    Map<MatchableName, CataloguedConsortium> catalogued = new LinkedHashMap<>();
    List<Participation> bids = new ArrayList<>();
    List<UteMembership> stated = new ArrayList<>();
    for (Map.Entry<MatchableName, List<PublishedBidder.Consortium>> entry :
        rows.named().entrySet()) {
      catalogued.put(
          entry.getKey(),
          storeOne(awardPoints, entry.getKey(), entry.getValue(), formalisations, bids, stated));
    }
    for (PublishedBidder.Consortium row : rows.nameless()) {
      bids.add(recordBid(awardPoints, row, null));
    }
    memberships.withdrawAbsent(licitacionId, stated);
    return new StoredConsortia(new ConsortiumOperadores(catalogued), bids);
  }

  /**
   * The procedure's consortium rows split into the ones a name can key and the ones it cannot, each
   * in the order the source named them.
   *
   * <p><strong>A consortium row publishing no name — or one that folds to nothing — cannot be
   * catalogued.</strong> R3 keys an identifier-less entry on its bid, and within one procedure the
   * published name is the only thing that tells one bid's consortium from another's; pooling two
   * such rows under one entry would attach one consortium's members to another's award.
   *
   * <p><strong>Its bid is written all the same, naming nobody.</strong> R16 records every operador
   * that applied and #19 requires every published bidder to be stored, with no exception for a
   * consortium — and {@link StoreLicitacionBidders} already takes exactly this view of a single
   * firm whose identifier was unusable: the bid is a fact the source published, and dropping it
   * would lose a bidder and leave the procedure's own participant count short. What is lost is the
   * catalogue entry, not the bid. Its member firms are not catalogued either: nothing would relate
   * them to, and an operador a bid creates but no participation reaches is unreachable by
   * construction.
   *
   * <p>Never observed in 613 measured bidder rows, both branches included.
   */
  private static ConsortiumRows consortiumRowsOf(Iterable<PublishedBidder> bidders) {
    Map<MatchableName, List<PublishedBidder.Consortium>> named = new LinkedHashMap<>();
    List<PublishedBidder.Consortium> nameless = new ArrayList<>();
    for (PublishedBidder bidder : bidders) {
      if (!(bidder instanceof PublishedBidder.Consortium consortium)) {
        continue;
      }
      MatchableName name = MatchableName.of(consortium.name()).orElse(null);
      if (name == null) {
        nameless.add(consortium);
      } else {
        named.computeIfAbsent(name, ignored -> new ArrayList<>()).add(consortium);
      }
    }
    return new ConsortiumRows(named, nameless);
  }

  /**
   * One consortium of this procedure: catalogued, its bids written, its members related to it.
   *
   * <p>The rows keep the order the source published them in, because the first is what supplies
   * the name the entry is catalogued under.
   */
  private CataloguedConsortium storeOne(
      AwardPoints awardPoints,
      MatchableName name,
      List<PublishedBidder.Consortium> rows,
      Iterable<PublishedFormalisation> formalisations,
      List<Participation> bids,
      List<UteMembership> stated) {
    String publishedName = rows.getFirst().name();
    Identity identity = identify(name, rows, formalisations);
    OperadorId uteId = catalogue(awardPoints.licitacionId(), name, publishedName, identity);
    for (PublishedBidder.Consortium row : rows) {
      bids.add(recordBid(awardPoints, row, uteId));
    }
    relateMembers(awardPoints.licitacionId(), uteId, rows, stated);
    return new CataloguedConsortium(uteId, identity.fiscalId(), identity.path());
  }

  /**
   * One consortium bidder row as the bid this procedure stores, at the award point its lote cell
   * names.
   *
   * <p>The operador is null for the one row that has none — a consortium published under no usable
   * name, which cannot be catalogued and whose bid is stored all the same. Every other bid names
   * the consortium's own operador, whether or not the source identified it, because a consortium is
   * a catalogue entry like any other party.
   *
   * <p><strong>Nothing here marks a bid as won</strong>, on {@link StoreLicitacionBidders}'s rule:
   * which bid won is the award table's answer rather than the bidder table's.
   */
  private Participation recordBid(
      AwardPoints awardPoints, PublishedBidder.Consortium row, @Nullable OperadorId party) {
    return participations.upsert(
        new Participation(
            awardPoints.licitacionId(), awardPoints.at(row.loteKey()), party, false));
  }

  /**
   * The identifier the procedure published for this consortium <em>anywhere</em>, and which
   * publication carried it.
   *
   * <p><strong>The bidder row is asked first and the formalisation second</strong>, which is the
   * order R17's <em>anywhere on the procedure</em> is read in: the bid is where the party is
   * published as a consortium at all, and the formalisation is the second place its identifier may
   * appear. Where both carry one and they differ, the bid's governs — the same direction the award
   * routing takes when a formalisation names a different party than the resolution.
   *
   * <p>A formalisation only answers where it names <em>this</em> consortium: the cell is compared
   * on the same fold every other name comparison in this package uses, and a formalisation naming
   * some other party is evidence about that party rather than about this one.
   */
  private static Identity identify(
      MatchableName name,
      Iterable<PublishedBidder.Consortium> rows,
      Iterable<PublishedFormalisation> formalisations) {
    for (PublishedBidder.Consortium row : rows) {
      FiscalIdentifier published = row.fiscalIdentifier();
      if (published != null) {
        return new Identity(published, AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
      }
    }
    for (PublishedFormalisation formalisation : formalisations) {
      FiscalIdentifier published = formalisation.fiscalIdentifier();
      if (published != null
          && name.equals(MatchableName.of(formalisation.contratistaName()).orElse(null))) {
        return new Identity(published, AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
      }
    }
    return new Identity(null, AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
  }

  /**
   * The catalogue entry this consortium is. Identified, it is an ordinary operador found by its
   * identifier and marked as a UTE; unidentified, it is the entry this procedure already
   * catalogued — found again through its own bid — and minted here only where there is none.
   *
   * <p><strong>The lookup does not care whether the entry it finds holds an identifier</strong>,
   * which is what keeps a consortium's identity from moving <em>backwards</em>. A record that
   * stops publishing the identifier it published before — a formalisation withdrawn at the source,
   * which is precisely the event the awardee gate exists to survive — would otherwise mint a
   * second, identifier-less entry beside the identified one this procedure already bid as. The bid
   * would move to the new entry while the award, gated on its published route, stayed on the old:
   * the procedure holding its consortium twice, which SPEC-0006 #40 forbids however it came about.
   * Reusing the entry keeps bid and award on one operador, and its members with them.
   *
   * <p>That is not the merge R3 forbids, because the lookup cannot reach beyond this procedure: it
   * joins from <em>this</em> procedure's own bids, so the only entry it can answer with is one this
   * procedure already catalogued under this name. Identity still never moves — the entry that was
   * identified stays identified, and this restatement simply declines to invent a rival for it.
   */
  private OperadorId catalogue(
      LicitacionId licitacionId,
      MatchableName name,
      @Nullable String publishedName,
      Identity identity) {
    FiscalIdentifier fiscalId = identity.fiscalId();
    if (fiscalId != null) {
      return identityOf(resolveOperador.resolveConsortium(fiscalId, publishedName));
    }
    return participations
        .findConsortiumOperador(licitacionId, name)
        .orElseGet(
            () -> identityOf(resolveOperador.catalogueUnidentifiedConsortium(publishedName)));
  }

  /**
   * Every member firm the consortium's rows named, catalogued under its own published identifier
   * and related to the consortium.
   *
   * <p><strong>A member whose identifier is unusable yields no operador and no membership</strong>,
   * on R16's rule, and costs the consortium and its other members nothing. All 80 member entries
   * measured carried an ordinary identifier, so this is the branch the sample never took.
   *
   * <p>A bid contributes no rank, so a member catalogued here is created and then left exactly as
   * it stands: being listed inside a consortium says who teamed up with whom, and nothing about
   * what any firm should be displayed as.
   *
   * <p>A member resolving to the consortium itself is skipped rather than stored: a consortium that
   * was its own member would be a row saying nothing, and it would let one keep <em>itself</em>
   * reachable under a predicate that counts a single visible membership.
   */
  private void relateMembers(
      LicitacionId licitacionId,
      OperadorId uteId,
      Iterable<PublishedBidder.Consortium> rows,
      List<UteMembership> stated) {
    for (PublishedBidder.Consortium row : rows) {
      for (PublishedConsortiumMember member : row.members()) {
        FiscalIdentifier published = member.fiscalIdentifier();
        if (published == null) {
          continue;
        }
        OperadorId memberId =
            identityOf(resolveOperador.resolveWithoutRanking(published, member.name()));
        if (!memberId.equals(uteId)) {
          UteMembership membership = new UteMembership(uteId, memberId, licitacionId);
          memberships.upsert(membership);
          stated.add(membership);
        }
      }
    }
  }

  private static OperadorId identityOf(OperadorEconomico operador) {
    return Objects.requireNonNull(operador.id(), "a catalogued operador must carry an identity");
  }

  /**
   * The split itself. The maps are built once and read once, so they are handed over rather than
   * copied — copying the named one through {@link Map#copyOf} would lose the source's ordering,
   * which is what keeps a procedure's consortia catalogued in the order it published them.
   */
  private record ConsortiumRows(
      Map<MatchableName, List<PublishedBidder.Consortium>> named,
      List<PublishedBidder.Consortium> nameless) {}

  /**
   * What the procedure published about this consortium's own identity: the identifier where any of
   * its publications carried one, and the route that would be recorded on an award attributed to
   * it. A null identifier is the ordinary case rather than a failure.
   */
  private record Identity(@Nullable FiscalIdentifier fiscalId, AwardeeResolutionPath path) {}
}
