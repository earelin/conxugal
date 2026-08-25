package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.ResolveOperador;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Stores a procedure's awards together with the operador each was awarded to, reaching that
 * operador by three routes tried in order — the formalisation, then the procedure's own bidder
 * rows, then a unique match against the catalogue — and recording on the award which route
 * answered.
 *
 * <p><strong>The order matters because only the last one infers.</strong> Measured over 284 award
 * rows the resolution table publishes no fiscal identifier at all, so:
 *
 * <ul>
 *   <li><strong>the formalisation</strong> publishes one for 58% of rows, in the
 *       {@code Contratista} cell that carries the name and the identifier together;
 *   <li><strong>the bidder rows</strong> publish one for a further 7%;
 *   <li><strong>a catalogue match on the published name</strong> is the sole route for the
 *       remaining 36%.
 * </ul>
 *
 * <p>The first two read the record rather than guess at it, and between them they carry 64% of all
 * award rows and 96% of those on a formalised procedure. What needs the third is a historical tail:
 * 59 of the 60 award-bearing procedures with nothing else to go on were published between 2008 and
 * 2012.
 *
 * <p><strong>The third route is bounded, and its bound is the whole reason it is allowed.</strong>
 * It <em>never creates an operador</em> — an invented one is what SPEC-0006 R5 forbids, and the
 * failure would be silent and permanent — it links only where exactly one <em>operador</em>
 * matches, and the link is recorded as {@link AwardeeResolutionPath#NAME_DERIVED} so it stays
 * distinguishable from one the source published and can be undone without guessing which it was.
 * <strong>It also ranks no name.</strong> The link is inferred, and a retained name is never
 * dropped except by being promoted, so letting a derived link move an operador's displayed
 * spelling would write an inference into the catalogue that no later import could take back.
 *
 * <p><strong>An award no route reaches is stored holding no operador</strong>, marked
 * {@link AwardeeResolutionPath#UNRESOLVED}: R16 and R25 make an award that names nobody a supported
 * outcome, and R25 refuses to make a resolvable awardee a condition of the procedure's visibility.
 * An unresolved awardee costs a link, never a procedure.
 *
 * <p><strong>An award whose awardee is a consortium row takes no route at all here.</strong>
 * Whether a consortium is catalogued under an identifier is a property of the procedure — the
 * bidder row and the formalisation are both allowed to supply it — rather than of the award row, so
 * attributing it belongs with the task that reads both. Left to the routing below, the catalogue
 * match would try {@code UTE PRACE-TABOADA RAMOS} against a catalogue that holds nothing like it,
 * which is an accident rather than a design.
 *
 * <p><strong>One transaction.</strong> An operador a resolution catalogues is created beside the
 * award that justified it, so a rollback cannot leave a catalogue entry no stored award points at.
 * {@link ResolveOperador} owns no boundary of its own — that is deliberate, and it makes supplying
 * one each caller's job.
 *
 * <p><strong>Normalisation for matching is not normalisation for storage.</strong> Both
 * name-matching steps compare through {@link MatchableName}, which folds case, accents,
 * punctuation and spacing; the award is stored holding its awardee's name exactly as published.
 */
@Singleton
public class StoreLicitacionAwards {

  private final ResolveOperador resolveOperador;
  private final OperadorRepository operadores;
  private final AwardRepository awards;

  public StoreLicitacionAwards(
      ResolveOperador resolveOperador, OperadorRepository operadores, AwardRepository awards) {
    this.resolveOperador = resolveOperador;
    this.operadores = operadores;
    this.awards = awards;
  }

  /**
   * Resolves and stores every award the record published, answering the stored awards in the order
   * the source named them.
   *
   * @param licitacion the stored procedure, which supplies the award point's procedure half and —
   *     as its publication date and its publication identifier — the rank a resolved award ranks
   *     its operador's name at
   * @param lotes the procedure's stored lotes, which is what an award row's lote cell is matched
   *     against
   * @param published the resolution table's rows as the source published them
   * @param formalisations the formalisation table's rows, the first route to an identifier
   * @param bidders the bidder table's rows, the second route to one and the only thing that says
   *     an awardee is a consortium
   */
  @Transactional
  public List<Award> store(
      Licitacion licitacion,
      List<Lote> lotes,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders) {
    Objects.requireNonNull(licitacion, "licitacion must not be null");
    Objects.requireNonNull(lotes, "lotes must not be null");
    Objects.requireNonNull(published, "published must not be null");
    Objects.requireNonNull(formalisations, "formalisations must not be null");
    Objects.requireNonNull(bidders, "bidders must not be null");
    LicitacionId licitacionId =
        Objects.requireNonNull(
            licitacion.id(), "the procedure must be stored before its awards are");
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    Routes routes = new Routes(formalisations, bidders, rankOf(licitacion));
    List<Award> stored = new ArrayList<>(published.size());
    for (PublishedAward row : published) {
      stored.add(awards.upsert(awardOf(row, licitacionId, awardPoints, routes)));
    }
    return List.copyOf(stored);
  }

  /**
   * One published award row as the award this procedure stores, awardee resolved. Every published
   * value is carried across as it was published — the awardee's name included, accents and
   * punctuation intact — and the only thing this adds is who that name reached and how.
   */
  private Award awardOf(
      PublishedAward row, LicitacionId licitacionId, AwardPoints awardPoints, Routes routes) {
    LoteId awardPoint = awardPoints.at(row.loteKey());
    Awardee awardee = awardeeOf(row, routes);
    return new Award(
        licitacionId,
        awardPoint,
        row.resolution(),
        row.diffusionDate(),
        row.amount(),
        row.executionPeriod(),
        row.awardeeName(),
        awardee.operadorId(),
        awardee.path());
  }

  /**
   * The three routes, in the order that puts both published ones ahead of the inferring one. A
   * route that cannot answer falls through to the next; the last one falling through is an award
   * that names nobody, which is an outcome rather than a failure.
   */
  private Awardee awardeeOf(PublishedAward row, Routes routes) {
    Optional<MatchableName> awardee = MatchableName.of(row.awardeeName());
    if (awardee.isPresent() && routes.bidsAsConsortium(awardee.get())) {
      return Awardee.nobody();
    }
    Contratista formalised = routes.contratistaFor(row, awardee.orElse(null));
    if (formalised != null) {
      return awarded(
          formalised.fiscalId(),
          nameOf(row, formalised),
          routes,
          AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
    }
    FiscalIdentifier bid = awardee.map(routes::bidIdentifierFor).orElse(null);
    if (bid != null) {
      return awarded(
          bid, row.awardeeName(), routes, AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
    }
    OperadorId catalogued = awardee.flatMap(this::uniqueCatalogueMatch).orElse(null);
    if (catalogued != null) {
      return new Awardee(catalogued, AwardeeResolutionPath.NAME_DERIVED);
    }
    return Awardee.nobody();
  }

  /**
   * The name path A catalogues its operador under: <strong>the award's</strong> where the
   * resolution published one, and the formalisation's where it did not.
   *
   * <p>The award's first, because the resolution states who was awarded — and where the two
   * publications name different parties this route is not taken at all, so the two can only differ
   * here by the award's being absent. Falling back matters more than it looks: an operador nothing
   * named before would otherwise be catalogued under the <em>empty</em> name, at this procedure's
   * own rank rather than at {@link NomeRank#unranked()} — so it would display as nothing until a
   * later contract strictly outranked it, and the promotion that finally displaced it would file
   * the empty string among the names the operador has borne.
   */
  private static @Nullable String nameOf(PublishedAward row, Contratista formalised) {
    return row.awardeeName() == null ? formalised.name() : row.awardeeName();
  }

  /**
   * The award as attributed by one of the two published routes: the operador that identifier names,
   * catalogued now if nothing named it before, accounted for against the name that publication
   * carried.
   */
  private Awardee awarded(
      FiscalIdentifier fiscalId,
      @Nullable String publishedName,
      Routes routes,
      AwardeeResolutionPath path) {
    NomeRank rank = routes.rank();
    OperadorEconomico operador =
        rank == null
            ? resolveOperador.resolveWithoutRanking(fiscalId, publishedName)
            : resolveOperador.resolve(fiscalId, publishedName, rank);
    return new Awardee(
        Objects.requireNonNull(operador.id(), "a catalogued operador must carry an identity"),
        path);
  }

  /**
   * The one operador the catalogue holds under this name, or nothing where it holds none or several
   * — the only inference this class makes, and the reason it is bounded to a unique match.
   *
   * <p><strong>The uniqueness is over operadores rather than over names.</strong> Two names
   * belonging to one operador are one match, which is what makes SPEC-0006 R15's retained set worth
   * matching against at all: it is how a firm stays findable under a name it no longer displays.
   * One name borne by two operadores is an ambiguity and declines — measured at 1 name in 268, and
   * that one a source typo.
   */
  private Optional<OperadorId> uniqueCatalogueMatch(MatchableName awardee) {
    Set<OperadorId> matches = operadores.findAllMatchingName(awardee);
    return matches.size() == 1 ? Optional.of(matches.iterator().next()) : Optional.empty();
  }

  /**
   * The rank a resolved award ranks its awardee's name at: the procedure's publication date and its
   * publication identifier as a number.
   *
   * <p>Null where the identifier is not a number, which is the rank-less path rather than a rank
   * engineered to lose — such an award still resolves its operador and still stores, and no name
   * advances from it. Ranking the identifier as text is what has to be refused: it would order
   * {@code "9"} above {@code "10"} and corrupt a tie-break the contratos menores family has already
   * populated, both families drawing from one publication id space.
   */
  private static @Nullable NomeRank rankOf(Licitacion licitacion) {
    OptionalLong publicationId = licitacion.publicationId().asNumber();
    if (publicationId.isEmpty()) {
      return null;
    }
    return new NomeRank(licitacion.publicationDate(), publicationId.getAsLong());
  }

  /**
   * What one formalisation row offers path A: the identifier its {@code Contratista} cell carried,
   * and the name published beside it in the same cell.
   */
  private record Contratista(FiscalIdentifier fiscalId, @Nullable String name) {}

  /** Which operador an award reached, and by which route. */
  private record Awardee(@Nullable OperadorId operadorId, AwardeeResolutionPath path) {

    /** An award no route reached: it holds no operador and says so. */
    static Awardee nobody() {
      return new Awardee(null, AwardeeResolutionPath.UNRESOLVED);
    }
  }

  /**
   * What one procedure's record offers the routing: its formalisation rows, its bidder rows, and
   * the rank its publication supplies. Read once per procedure rather than per award, since every
   * award row asks the same two tables the same questions.
   */
  private record Routes(
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders,
      @Nullable NomeRank rank) {

    Routes {
      formalisations = List.copyOf(formalisations);
      bidders = List.copyOf(bidders);
    }

    /**
     * Whether this name is one the procedure's bidder table published as a consortium. Such an
     * award is left unattributed here whatever else could reach an identifier for it — including a
     * formalisation, which is precisely one of the two places a consortium's own identifier is
     * published and therefore evidence the task that attributes consortia needs to weigh whole.
     */
    boolean bidsAsConsortium(MatchableName awardee) {
      for (PublishedBidder bidder : bidders) {
        if (bidder instanceof PublishedBidder.Consortium consortium
            && awardee.equals(MatchableName.of(consortium.name()).orElse(null))) {
          return true;
        }
      }
      return false;
    }

    /**
     * The contratista the formalisation of this award's own award point published, or null where
     * there is no such row, where its {@code Contratista} cell yielded no identifier, or where
     * every such row named a different party than the resolution did.
     *
     * <p><strong>A different party means the award's name governs and this route is not
     * taken.</strong> The resolution states who was awarded; a formalisation naming someone else is
     * a fact about signing, and attributing the award to that party would put money against an
     * operador the source never awarded it to. Two names are only compared where the source
     * published both — one it did not publish is not a disagreement.
     *
     * <p><strong>Every row for the award point is read, not just the first.</strong> Nothing
     * guarantees the parse emits one formalisation per award point, and a row that publishes no
     * identifier — or names another party — is a route that did not answer rather than evidence
     * against the row beside it. Stopping at the first would demote a published route to a derived
     * one on the strength of a row the store, which upserts these on the award point, would not
     * even have kept.
     *
     * <p>The join is on the <strong>reduced</strong> lote key, which both published records have
     * already applied: raw, a formalisation writing {@code 01} would miss an award row writing
     * {@code 1} and silently demote a published route to a derived one.
     */
    @Nullable Contratista contratistaFor(PublishedAward row, @Nullable MatchableName awardee) {
      for (PublishedFormalisation formalisation : formalisations) {
        FiscalIdentifier published = formalisation.fiscalIdentifier();
        if (published == null || !Objects.equals(formalisation.loteKey(), row.loteKey())) {
          continue;
        }
        MatchableName contratista = MatchableName.of(formalisation.contratistaName()).orElse(null);
        boolean namesAnotherParty =
            awardee != null && contratista != null && !awardee.equals(contratista);
        if (!namesAnotherParty) {
          return new Contratista(published, formalisation.contratistaName());
        }
      }
      return null;
    }

    /**
     * The identifier the one bidder of this name published, or null where no bidder row carries
     * that name, where the row that does published none, or where the rows that do disagree.
     *
     * <p><strong>Scoped to the procedure rather than to the award point.</strong> This is the
     * catalogue match narrowed to one record, and a firm that bid on one lote and was awarded
     * another is the same firm publishing the same identifier — while a name that reaches two
     * parties is no more usable here than it would be against the catalogue.
     *
     * <p><strong>Two bidders of one name decline, and a row publishing no identifier is one of
     * them.</strong> An identifier-less row is still a party the source published under that name,
     * so it is exactly the ambiguity this route exists to refuse — taking the other row's
     * identifier would attribute the award to a party that may not be the awardee, and mark it
     * <em>published</em> rather than derived. What does <em>not</em> make an ambiguity is one firm
     * bidding at two award points: two rows, one identifier, one party, and the ordinary shape of a
     * procedure with lotes.
     */
    @Nullable FiscalIdentifier bidIdentifierFor(MatchableName awardee) {
      FiscalIdentifier only = null;
      for (PublishedBidder bidder : bidders) {
        if (!(bidder instanceof PublishedBidder.SingleFirm firm)
            || !awardee.equals(MatchableName.of(firm.name()).orElse(null))) {
          continue;
        }
        FiscalIdentifier published = firm.fiscalIdentifier();
        if (published == null || (only != null && !only.equals(published))) {
          return null;
        }
        only = published;
      }
      return only;
    }
  }
}
