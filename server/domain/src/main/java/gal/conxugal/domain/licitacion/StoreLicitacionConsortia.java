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
   * Catalogues every consortium the record published, stores its bids and its membership, and
   * answers which operador each became — which is what the award resolution then attributes to.
   *
   * @param licitacionId the stored procedure these bids were made for
   * @param lotes the procedure's stored lotes, which is what a bidder row's lote cell is matched
   *     against
   * @param bidders the bidder table's rows as the source published them, single firms included
   * @param formalisations the formalisation table's rows, the second place a consortium's own
   *     identifier is published and often the only one
   */
  @Transactional
  public ConsortiumOperadores store(
      LicitacionId licitacionId,
      List<Lote> lotes,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Objects.requireNonNull(lotes, "lotes must not be null");
    Objects.requireNonNull(bidders, "bidders must not be null");
    Objects.requireNonNull(formalisations, "formalisations must not be null");
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    Map<MatchableName, List<PublishedBidder.Consortium>> named = new LinkedHashMap<>();
    List<PublishedBidder.Consortium> nameless = new ArrayList<>();
    group(bidders, named, nameless);
    Map<MatchableName, CataloguedConsortium> catalogued = new LinkedHashMap<>();
    for (Map.Entry<MatchableName, List<PublishedBidder.Consortium>> entry : named.entrySet()) {
      catalogued.put(
          entry.getKey(),
          storeOne(awardPoints, entry.getKey(), entry.getValue(), formalisations));
    }
    for (PublishedBidder.Consortium row : nameless) {
      participations.upsert(
          new Participation(
              awardPoints.licitacionId(), awardPoints.at(row.loteKey()), null, false));
    }
    return new ConsortiumOperadores(catalogued);
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
  private static void group(
      Iterable<PublishedBidder> bidders,
      Map<MatchableName, List<PublishedBidder.Consortium>> named,
      List<PublishedBidder.Consortium> nameless) {
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
  }

  /** One consortium of this procedure: catalogued, its bids written, its members related to it. */
  private CataloguedConsortium storeOne(
      AwardPoints awardPoints,
      MatchableName name,
      List<PublishedBidder.Consortium> rows,
      List<PublishedFormalisation> formalisations) {
    String publishedName = rows.getFirst().name();
    Identity identity = identify(name, rows, formalisations);
    OperadorId uteId = catalogue(awardPoints.licitacionId(), name, publishedName, identity);
    for (PublishedBidder.Consortium row : rows) {
      participations.upsert(
          new Participation(
              awardPoints.licitacionId(), awardPoints.at(row.loteKey()), uteId, false));
    }
    relateMembers(uteId, rows);
    return new CataloguedConsortium(uteId, identity.fiscalId(), identity.path());
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
   * identifier and marked as a UTE; unidentified, it is the entry this procedure minted — found
   * again through its own bid where an earlier import already made one, and minted here where none
   * did.
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
  private void relateMembers(OperadorId uteId, Iterable<PublishedBidder.Consortium> rows) {
    for (PublishedBidder.Consortium row : rows) {
      for (PublishedConsortiumMember member : row.members()) {
        FiscalIdentifier published = member.fiscalIdentifier();
        if (published == null) {
          continue;
        }
        OperadorId memberId =
            identityOf(resolveOperador.resolveWithoutRanking(published, member.name()));
        if (!memberId.equals(uteId)) {
          memberships.upsert(new UteMembership(uteId, memberId));
        }
      }
    }
  }

  private static OperadorId identityOf(OperadorEconomico operador) {
    return Objects.requireNonNull(operador.id(), "a catalogued operador must carry an identity");
  }

  /**
   * What the procedure published about this consortium's own identity: the identifier where any of
   * its publications carried one, and the route that would be recorded on an award attributed to
   * it. A null identifier is the ordinary case rather than a failure.
   */
  private record Identity(@Nullable FiscalIdentifier fiscalId, AwardeeResolutionPath path) {}
}
