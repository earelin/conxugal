package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Stores one procedure whole — the aggregate, its lotes, its classifications, its bidders, its
 * awards and its formalisations — reconciling what the store already holds to what the record now
 * publishes, in one transaction.
 *
 * <p><strong>Reconciliation is the ordinary path here, not an exception.</strong> Every retrieval
 * of a record restates the whole procedure, so almost every call after the first is a restatement
 * of something already stored. The procedure is matched by its <em>publication identifier</em> —
 * the source's natural key, never the identity the system assigned — and refreshed in place; each
 * of its six child tables is written from what the record publishes and then reconciled, so a lote,
 * a classification, a bidder, an award or a formalisation the source has stopped publishing is
 * <strong>retained and marked withdrawn</strong>. Nothing an import does deletes a row, and that is
 * a promise the whole operadores catalogue rests on: its privacy analysis assumes every feeding
 * family's removal rule is non-destructive and reversible, so a participation an ordinary import
 * could erase would break it for far more than this procedure.
 *
 * <p><strong>It takes the listing entry and the parsed record together, and needs both.</strong>
 * Four of the aggregate's values are published only by the listing — both dates and the state's
 * <em>code</em> as well as its label — and the record publishes the state's label alone, which for
 * the two codes sharing one is ambiguous. On the retry path there is no listing entry, which is why
 * the outstanding-record ledger carries those same four values; the second overload takes them from
 * there. <strong>Everything else comes from the record in both cases</strong>, deliberately: it is
 * what makes a retried procedure and a freshly walked one the same stored procedure rather than two
 * readings that could disagree.
 *
 * <p><strong>A procedure absent from a later import is not this class's business.</strong> Absence
 * at the source is not evidence of withdrawal, and the explicit removal that is belongs to a later
 * feature. Nothing here compares the store against a listing, and this class sees one procedure at
 * a time — it knows nothing of the Órgano's history as a whole.
 *
 * <p><strong>Order matters in three places</strong>, and each is load-bearing rather than
 * incidental:
 *
 * <ul>
 *   <li>the <strong>vocabularies and the procedure</strong> come first, because every child hangs
 *       off an identity the procedure's own upsert answers with, and every vocabulary reference the
 *       procedure carries must already be stored;
 *   <li>the <strong>lotes</strong> come next, because they are the award points every other table
 *       joins through — and they are taken from the record's own lote list, which is the union of
 *       every lote cell its five tables name, so no published row can name a point the lotes miss;
 *   <li><strong>consortia before bidders before awards</strong>. Whether a consortium is identified
 *       is a property of the procedure rather than of one row — its bidder row and its
 *       formalisation both supply the identifier — so it is settled once, before any award is
 *       attributed, and the awards take the answer rather than deriving it again.
 * </ul>
 *
 * <p>The bidder table is reconciled <strong>once</strong>, after both halves of it have been
 * written: a consortium's bids come from one collaborator and a single firm's from another, and
 * reconciling either half alone would withdraw every bid the other had just stored.
 *
 * <p><strong>One transaction, and the whole point of it.</strong> A procedure and its children
 * commit together or not at all, so a failure part-way through leaves nothing partially written —
 * no procedure holding half its awards, and no catalogue entry that no stored row points at.
 */
@Singleton
public class StoreLicitacion {

  private final LicitacionRepository licitacions;
  private final LicitacionStateRepository states;
  private final LicitacionContractTypeRepository contractTypes;
  private final LicitacionProcedureTypeRepository procedureTypes;
  private final LicitacionTramitacionTypeRepository tramitacionTypes;
  private final LoteRepository lotes;
  private final CpvRepository cpvs;
  private final CpvClassificationRepository cpvClassifications;
  private final NutRepository nuts;
  private final NutClassificationRepository nutClassifications;
  private final FormalisationRepository formalisations;
  private final ParticipationRepository participations;
  private final StoreLicitacionConsortia storeConsortia;
  private final StoreLicitacionBidders storeBidders;
  private final StoreLicitacionAwards storeAwards;

  public StoreLicitacion(
      LicitacionRepository licitacions,
      LicitacionStateRepository states,
      LicitacionContractTypeRepository contractTypes,
      LicitacionProcedureTypeRepository procedureTypes,
      LicitacionTramitacionTypeRepository tramitacionTypes,
      LoteRepository lotes,
      CpvRepository cpvs,
      CpvClassificationRepository cpvClassifications,
      NutRepository nuts,
      NutClassificationRepository nutClassifications,
      FormalisationRepository formalisations,
      ParticipationRepository participations,
      StoreLicitacionConsortia storeConsortia,
      StoreLicitacionBidders storeBidders,
      StoreLicitacionAwards storeAwards) {
    this.licitacions = licitacions;
    this.states = states;
    this.contractTypes = contractTypes;
    this.procedureTypes = procedureTypes;
    this.tramitacionTypes = tramitacionTypes;
    this.lotes = lotes;
    this.cpvs = cpvs;
    this.cpvClassifications = cpvClassifications;
    this.nuts = nuts;
    this.nutClassifications = nutClassifications;
    this.formalisations = formalisations;
    this.participations = participations;
    this.storeConsortia = storeConsortia;
    this.storeBidders = storeBidders;
    this.storeAwards = storeAwards;
  }

  /**
   * Stores the procedure this listing entry and this record describe, answering the identity it is
   * stored under and whether the store held it before.
   *
   * <p>The two must describe the same publication; a caller pairing a record with another entry's
   * listing row would store one procedure's attributes under the other's dates and state.
   */
  @Transactional
  public UpsertOutcome store(
      LicitacionListingEntry entry, LicitacionRecord record, OrganoId organoId) {
    Objects.requireNonNull(entry, "entry must not be null");
    Objects.requireNonNull(record, "record must not be null");
    requireSamePublication(entry.publicationId(), record);
    return store(
        record,
        organoId,
        entry.publicationDate(),
        entry.lastModified(),
        entry.stateCode(),
        entry.stateLabel());
  }

  /**
   * The same store, driven from the ledger rather than from a listing page: a procedure an earlier
   * run could not retrieve, retried before the cursor resumes and with no listing row in hand.
   *
   * <p>It produces the <strong>same</strong> procedure, because the ledger keeps exactly the four
   * values the record cannot answer for and everything else comes from the record either way.
   */
  @Transactional
  public UpsertOutcome store(
      LicitacionOutstandingRecord outstanding, LicitacionRecord record, OrganoId organoId) {
    Objects.requireNonNull(outstanding, "outstanding must not be null");
    Objects.requireNonNull(record, "record must not be null");
    requireSamePublication(outstanding.publicationId(), record);
    return store(
        record,
        organoId,
        outstanding.publicationDate(),
        outstanding.lastModified(),
        outstanding.stateCode(),
        outstanding.stateLabel());
  }

  private UpsertOutcome store(
      LicitacionRecord record,
      OrganoId organoId,
      @Nullable LocalDate publicationDate,
      @Nullable LocalDate lastModified,
      int stateCode,
      @Nullable String stateLabel) {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Licitacion published =
        procedureOf(record, organoId, publicationDate, lastModified, stateCode, stateLabel);
    UpsertOutcome outcome = licitacions.upsert(published);
    Licitacion procedure = storedAs(published, outcome.id());
    List<Lote> storedLotes = storeLotes(outcome.id(), record.lotes());
    storeClassifications(outcome.id(), storedLotes, record);
    participations.markWinners(outcome.id(), storeCompetition(procedure, storedLotes, record));
    return outcome;
  }

  /**
   * The procedure as this import states it: the record's own values, and beside them the four the
   * record does not publish.
   *
   * <p>The object and the base budget are taken from the <strong>record</strong> even though the
   * listing publishes both, which is what keeps the listing-driven store and the ledger-driven one
   * producing one procedure rather than two that could differ by which source was in hand.
   */
  private Licitacion procedureOf(
      LicitacionRecord record,
      OrganoId organoId,
      @Nullable LocalDate publicationDate,
      @Nullable LocalDate lastModified,
      int stateCode,
      @Nullable String stateLabel) {
    return new Licitacion(
        record.publicationId(),
        organoId,
        publicationDate,
        lastModified,
        states.upsert(new LicitacionState(stateCode, stateLabel)),
        record.expediente(),
        record.obxecto(),
        contractTypeOf(record.contractType()),
        procedureTypeOf(record.procedureType()),
        tramitacionTypeOf(record.tramitacionType()),
        record.loteCount(),
        amountOf(record.baseBudget()),
        amountOf(record.estimatedValue()));
  }

  /**
   * The same statement of the procedure, now carrying the identity the store assigned it, which is
   * what its children hang off and what the award resolution ranks an operador's name at.
   *
   * <p>It is not a read of the stored row and does not claim to be one: this is what the source
   * publishes right now, and an import publishes no withdrawal.
   */
  private static Licitacion storedAs(Licitacion published, LicitacionId id) {
    return new Licitacion(
        id,
        published.publicationId(),
        published.organoId(),
        published.publicationDate(),
        published.lastModified(),
        published.state(),
        published.expediente(),
        published.obxecto(),
        published.contractType(),
        published.procedureType(),
        published.tramitacionType(),
        published.loteCount(),
        published.baseBudget(),
        published.estimatedValue(),
        published.withdrawn());
  }

  /** Every lote the record names, stored, and every one it no longer names, withdrawn. */
  private List<Lote> storeLotes(LicitacionId licitacionId, List<PublishedLote> published) {
    List<Lote> stored = new ArrayList<>(published.size());
    for (PublishedLote lote : published) {
      stored.add(
          lotes.upsert(
              new Lote(
                  licitacionId, lote.identifier(), lote.description(), lote.estimatedValue())));
    }
    lotes.withdrawAbsent(licitacionId, stored.stream().map(Lote::id).toList());
    return List.copyOf(stored);
  }

  /**
   * The two classification tables, each cited entry catalogued before the row citing it.
   *
   * <p>A row naming no lote is the procedure as a whole and is stored that way — measured, a
   * procedure with two lotes and two separate awards classified neither of them.
   */
  private void storeClassifications(
      LicitacionId licitacionId, List<Lote> lotes, LicitacionRecord record) {
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    List<CpvClassificationId> storedCpvs = new ArrayList<>(record.cpvClassifications().size());
    for (PublishedCpvClassification published : record.cpvClassifications()) {
      Cpv entry = cpvs.upsert(new Cpv(null, published.code(), published.description()));
      storedCpvs.add(
          cpvClassifications
              .upsert(
                  new CpvClassification(
                      licitacionId,
                      awardPoints.at(published.loteKey()),
                      entry,
                      published.diffusionDate()))
              .id());
    }
    cpvClassifications.withdrawAbsent(licitacionId, storedCpvs);

    List<NutClassificationId> storedNuts = new ArrayList<>(record.nutClassifications().size());
    for (PublishedNutClassification published : record.nutClassifications()) {
      Nut entry = nuts.upsert(new Nut(null, published.code(), published.description()));
      storedNuts.add(
          nutClassifications
              .upsert(
                  new NutClassification(
                      licitacionId,
                      awardPoints.at(published.loteKey()),
                      entry,
                      published.diffusionDate()))
              .id());
    }
    nutClassifications.withdrawAbsent(licitacionId, storedNuts);
  }

  /**
   * The four tables that say who competed and who won, in the order that settles a consortium's
   * identity before anything is attributed to it. Answers the procedure's awards, which is what
   * then carries each win back to the bid that made it.
   *
   * <p>The formalisations are stored — and reconciled — before the awards read them, because a
   * formalisation the source withdrew is exactly what the awardee gate exists to survive: the row
   * goes, and the published link it once justified stays.
   */
  private List<Award> storeCompetition(
      Licitacion procedure, List<Lote> lotes, LicitacionRecord record) {
    LicitacionId licitacionId = Objects.requireNonNull(procedure.id());
    StoredConsortia consortia =
        storeConsortia.store(licitacionId, lotes, record.bidders(), record.formalisations());
    List<Participation> bids = new ArrayList<>(consortia.bids());
    bids.addAll(storeBidders.store(licitacionId, lotes, record.bidders()));
    participations.withdrawAbsent(licitacionId, bids.stream().map(Participation::id).toList());

    storeFormalisations(licitacionId, lotes, record.formalisations());
    return storeAwards.store(
        procedure,
        lotes,
        record.awards(),
        record.formalisations(),
        record.bidders(),
        consortia.operadores());
  }

  private void storeFormalisations(
      LicitacionId licitacionId, List<Lote> lotes, List<PublishedFormalisation> published) {
    AwardPoints awardPoints = AwardPoints.of(licitacionId, lotes);
    List<FormalisationId> stored = new ArrayList<>(published.size());
    for (PublishedFormalisation row : published) {
      stored.add(
          formalisations
              .upsert(
                  new Formalisation(
                      licitacionId,
                      awardPoints.at(row.loteKey()),
                      row.formalisationDate(),
                      row.contratistaName(),
                      row.fiscalIdentifier(),
                      row.nationality(),
                      row.amount()))
              .id());
    }
    formalisations.withdrawAbsent(licitacionId, stored);
  }

  private static @Nullable Money amountOf(@Nullable PublishedAmount amount) {
    return amount == null ? null : amount.amount();
  }

  private @Nullable LicitacionContractType contractTypeOf(@Nullable String name) {
    return name == null ? null : contractTypes.upsert(new LicitacionContractType(name));
  }

  private @Nullable LicitacionProcedureType procedureTypeOf(@Nullable String name) {
    return name == null ? null : procedureTypes.upsert(new LicitacionProcedureType(name));
  }

  private @Nullable LicitacionTramitacionType tramitacionTypeOf(@Nullable String name) {
    return name == null ? null : tramitacionTypes.upsert(new LicitacionTramitacionType(name));
  }

  /**
   * The record and the entry beside it must describe one publication. Nothing downstream would
   * notice the mismatch — the record supplies the attributes and the entry the dates and the state,
   * and both are plausible on their own — so it is refused here rather than stored.
   */
  private static void requireSamePublication(PublicationId expected, LicitacionRecord record) {
    if (!expected.equals(record.publicationId())) {
      throw new IllegalArgumentException(
          "the record published under %s does not describe publication %s"
              .formatted(record.publicationId().value(), expected.value()));
    }
  }
}
