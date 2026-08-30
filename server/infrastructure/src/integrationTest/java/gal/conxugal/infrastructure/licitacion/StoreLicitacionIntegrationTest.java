package gal.conxugal.infrastructure.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.licitacion.AwardeeResolutionPath;
import gal.conxugal.domain.licitacion.LicitacionListingEntry;
import gal.conxugal.domain.licitacion.LicitacionOutstandingRecord;
import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.PublishedAward;
import gal.conxugal.domain.licitacion.PublishedBidder;
import gal.conxugal.domain.licitacion.PublishedConsortiumMember;
import gal.conxugal.domain.licitacion.PublishedCpvClassification;
import gal.conxugal.domain.licitacion.PublishedFormalisation;
import gal.conxugal.domain.licitacion.PublishedLote;
import gal.conxugal.domain.licitacion.PublishedNutClassification;
import gal.conxugal.domain.licitacion.StoreLicitacion;
import gal.conxugal.domain.licitacion.UpsertOperation;
import gal.conxugal.domain.licitacion.UpsertOutcome;
import gal.conxugal.domain.licitacion.VatBasis;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * What storing a procedure twice leaves behind, against a real PostgreSQL: the same rows with the
 * same identities, and — where the second record publishes less — the same rows with a withdrawal
 * marker rather than fewer rows.
 *
 * <p>These belong against a real database rather than beside the unit tests because almost every
 * claim is about <strong>what the store still holds</strong>: a row retained and marked rather than
 * deleted, an identity unchanged across a restatement, a membership another procedure still keeps
 * visible, and a failed store leaving nothing behind. None of that is observable through a mock.
 *
 * <p><strong>{@code transactional = false}</strong>, on
 * {@link LicitacionConsortiaDerivationIntegrationTest}'s reasoning, and here it is load-bearing
 * twice over: a second store finding what the first wrote has to be true for production's reason,
 * and the rollback test has nothing to prove if an outer transaction is rolling everything back
 * anyway.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreLicitacionIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2012, 5, 4);
  private static final LocalDate MODIFIED_ON = LocalDate.of(2012, 6, 28);
  private static final Money AWARDED = new Money(new BigDecimal("206996.66"));
  private static final Money BUDGET = new Money(new BigDecimal("250000.00"));
  private static final String EQUINSE = "EQUINSE, S.A.";
  private static final FiscalIdentifier EQUINSE_ID = new FiscalIdentifier("A41111220");
  private static final String PLUSMER = "PLUS-MER, S.L.";
  private static final FiscalIdentifier PLUSMER_ID = new FiscalIdentifier("B36801942");
  /** The same firm under another identifier, as a formalisation that corrects one publishes it. */
  private static final FiscalIdentifier CORRECTED_ID = new FiscalIdentifier("B36801943");
  private static final String UTE = "UTE PRACE-TABOADA RAMOS";
  private static final FiscalIdentifier UTE_ID = new FiscalIdentifier("U88779475");
  private static final String PRACE = "PRACE SERVICIOS Y OBRAS SA";
  private static final FiscalIdentifier PRACE_ID = new FiscalIdentifier("A70319678");
  private static final String TABOADA = "CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU";
  private static final FiscalIdentifier TABOADA_ID = new FiscalIdentifier("B94181807");

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  StoreLicitacion storeLicitacion;

  private OrganoId organoId;

  @BeforeEach
  void anOrganoConvenesThem() throws Exception {
    organoId = new OrganoId(insertOrgano("axencia-turismo"));
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // The ordinary path after the first run, and the one every other claim rests on: a restatement is
  // an update in place, so the second store adds nothing and reports so.
  @Test
  void storing_the_same_entry_and_record_twice_changes_nothing_and_adds_nothing() throws Exception {
    LicitacionRecord record = wholeRecord();
    UpsertOutcome first = store(listing(), record);

    UpsertOutcome second = store(listing(), record);

    assertThat(second.operation()).isEqualTo(UpsertOperation.REFRESHED);
    assertThat(second.id()).isEqualTo(first.id());
    assertThat(rowCount("licitacion")).isOne();
    assertThat(rowCount("licitacion_lote")).isEqualTo(2);
    assertThat(rowCount("licitacion_award")).isEqualTo(2);
    assertThat(rowCount("licitacion_formalisation")).isOne();
    assertThat(rowCount("licitacion_cpv")).isOne();
    assertThat(rowCount("licitacion_nut")).isOne();
    assertThat(withdrawnRowCount("licitacion_lote")).isZero();
    assertThat(withdrawnRowCount("licitacion_award")).isZero();
    assertThat(withdrawnRowCount("licitacion_participation")).isZero();
  }

  // Four values the record cannot publish. The state's label alone is ambiguous -- 101 and 102 are
  // both "Histórico" -- so the code is what the procedure is keyed to.
  @Test
  void the_procedure_carries_the_listing_entrys_state_code_label_and_both_dates() throws Exception {
    store(listing(), wholeRecord());

    assertThat(one("SELECT publication_date::text AS v FROM licitacion")).isEqualTo("2012-05-04");
    assertThat(one("SELECT last_modified::text AS v FROM licitacion")).isEqualTo("2012-06-28");
    assertThat(one("SELECT s.code::text AS v FROM licitacion l JOIN licitacion_state s"
            + " ON s.id = l.state_id"))
        .isEqualTo("101");
    assertThat(one("SELECT s.label AS v FROM licitacion l JOIN licitacion_state s"
            + " ON s.id = l.state_id"))
        .isEqualTo("Histórico");
  }

  // The retry path: no listing row is in hand, so the ledger's four values stand in for it and the
  // procedure that lands is the same one.
  @Test
  void store_driven_from_the_ledger_produces_the_same_procedure() throws Exception {
    store(listing(), wholeRecord());
    final List<String> fromListing = procedureColumns();
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
    organoId = new OrganoId(insertOrgano("axencia-turismo"));

    storeLicitacion.store(ledger(), wholeRecord(), organoId);

    assertThat(procedureColumns()).isEqualTo(fromListing);
  }

  // R13's refresh: the same procedure, not a second one, and the identity its children hang off is
  // the one it already had.
  @Test
  void restated_record_refreshes_the_procedure_in_place() throws Exception {
    UpsertOutcome first = store(listing(), wholeRecord());

    UpsertOutcome second =
        store(
            listing(),
            recordOf(
                "Servizo de limpeza, ampliado",
                List.of(lote("1"), lote("2")),
                List.of(award("1", EQUINSE), award("2", PLUSMER)),
                List.of(singleFirm("1", EQUINSE, EQUINSE_ID)),
                List.of(formalisation("1", EQUINSE, EQUINSE_ID)),
                List.of(cpv("45000000")),
                List.of(nut("ES11"))));

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(rowCount("licitacion")).isOne();
    assertThat(one("SELECT obxecto AS v FROM licitacion"))
        .isEqualTo("Servizo de limpeza, ampliado");
  }

  // R13's retention, over all five child kinds at once -- the formalisation included, because it is
  // the row whose disappearance the awardee gate exists to survive.
  @Test
  void every_child_the_record_stops_publishing_is_retained_and_marked_withdrawn() throws Exception {
    store(listing(), wholeRecord());

    store(listing(), strippedRecord());

    // Nothing was deleted: the counts are what the fuller record left.
    assertThat(rowCount("licitacion_lote")).isEqualTo(2);
    assertThat(rowCount("licitacion_award")).isEqualTo(2);
    assertThat(rowCount("licitacion_formalisation")).isOne();
    assertThat(rowCount("licitacion_cpv")).isOne();
    assertThat(rowCount("licitacion_nut")).isOne();
    assertThat(rowCount("licitacion_participation")).isEqualTo(2);
    // And what the second record no longer publishes is marked rather than gone.
    assertThat(withdrawnRowCount("licitacion_lote")).isOne();
    assertThat(withdrawnRowCount("licitacion_award")).isOne();
    assertThat(withdrawnRowCount("licitacion_formalisation")).isOne();
    assertThat(withdrawnRowCount("licitacion_cpv")).isOne();
    assertThat(withdrawnRowCount("licitacion_nut")).isOne();
    assertThat(withdrawnRowCount("licitacion_participation")).isOne();
  }

  // SPEC-0006 #39 for the 33-of-35 case: an unidentified UTE is an operador per bid, so no other
  // procedure can reach it and withdrawing the bid takes its membership with it.
  @Test
  void withdrawing_an_unidentified_utes_bid_withdraws_every_membership_under_it() throws Exception {
    store(listing(), consortiumRecord(null));

    store(listing(), recordOf("Obra", List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of()));

    UUID uteId = operadorIdOfIdentifierLessUte();
    assertThat(visibleMembersOf(uteId)).isEmpty();
    assertThat(rowCount("operador_ute_membership")).isEqualTo(2);
    // The member firm's only tie was that membership, so it is reachable through nothing now.
    assertThat(visibleMembershipsOf(operadorIdOf(PRACE_ID))).isZero();
  }

  // SPEC-0006 #39 and #40 for the identified minority: one UTE operador published by two
  // procedures, so a withdrawal at the first must not hide what the second still states.
  @Test
  void identified_ute_keeps_membership_the_other_procedure_still_states() throws Exception {
    store(listing(), consortiumRecord(UTE_ID));
    OrganoId first = organoId;
    organoId = new OrganoId(insertOrgano("outro-organo"));
    // The second procedure states one of the two members and not the other.
    store(
        listingFor("822055"),
        consortiumRecordFor("822055", UTE_ID, member(PRACE, PRACE_ID)));
    organoId = first;

    store(listing(), recordOf("Obra", List.of(), List.of(), List.of(), List.of(), List.of(),
        List.of()));

    UUID uteId = operadorIdOf(UTE_ID);
    // PRACE survives on the second procedure's statement; TABOADA, which only the first ever
    // stated, does not -- lost only because no procedure states it any more.
    assertThat(visibleMembersOf(uteId)).containsExactly(operadorIdOf(PRACE_ID));
  }

  // The mechanism behind the historical tail closing, end to end: adxudicado with a name-derived
  // awardee becomes formalizado, and the identifier the formalisation publishes takes over.
  @Test
  void formalising_re_resolves_name_derived_awardee_to_published_identifier()
      throws Exception {
    // Another procedure catalogues PLUS-MER under one identifier, so this one's award — which
    // publishes no identifier of its own — matches it by name and nothing else.
    store(listingFor("822055"), biddersOnlyRecordFor("822055"));
    store(listing(), awardOnlyRecord(PLUSMER, List.of()));
    assertThat(awardPathOf("822054")).isEqualTo(AwardeeResolutionPath.NAME_DERIVED.name());
    assertThat(awardeeOf("822054")).isEqualTo(operadorIdOf(PLUSMER_ID));

    // Formalizado: the Contratista cell now publishes the awardee's identifier, and it is not the
    // one the name match guessed at.
    store(listing(), awardOnlyRecord(PLUSMER, List.of(formalisation(null, PLUSMER, CORRECTED_ID))));

    assertThat(awardPathOf("822054"))
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION.name());
    assertThat(awardeeOf("822054"))
        .isEqualTo(operadorIdOf(CORRECTED_ID))
        .isNotEqualTo(operadorIdOf(PLUSMER_ID));
  }

  // The gate: a formalisation withdrawn at the source is not evidence that the identifier it
  // published was wrong, so the published link survives a record whose routing answers lower.
  //
  // A second operador is catalogued under the same published name first, so the restatement's name
  // match is ambiguous and reaches nobody. Without the gate the award would be stored naming
  // nobody, which is what makes the operador assertion below bite rather than restate the setup.
  @Test
  void restatement_that_would_lower_the_resolution_path_does_not_write() throws Exception {
    store(listingFor("822055"), biddersOnlyRecordFor("822055", EQUINSE, CORRECTED_ID));
    store(listing(), awardOnlyRecord(EQUINSE, List.of(formalisation(null, EQUINSE, EQUINSE_ID))));
    UUID published = awardeeOf("822054");
    assertThat(published).isEqualTo(operadorIdOf(EQUINSE_ID));

    store(listing(), awardOnlyRecord(EQUINSE, List.of()));

    assertThat(awardPathOf("822054"))
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION.name());
    assertThat(awardeeOf("822054")).isEqualTo(published);
  }

  // The consortium case TASK-0013's ordering argument does not cover: a procedure moving to
  // formalizado publishes an identifier the first import had nowhere to read. The old entry is
  // withdrawn out of visibility, never folded into the new -- ADR-0023 forbids re-partitioning it.
  @Test
  void consortium_the_source_starts_identifying_is_catalogued_afresh_and_never_merged()
      throws Exception {
    store(listing(), consortiumRecord(null));
    UUID minted = operadorIdOfIdentifierLessUte();

    store(listing(), consortiumRecord(UTE_ID));

    UUID identified = operadorIdOf(UTE_ID);
    assertThat(identified).isNotEqualTo(minted);
    // Both entries survive -- nothing merges them -- but only the identified one is still bid as.
    assertThat(rowCount("operador_economico WHERE ute")).isEqualTo(2);
    assertThat(visibleBidders("822054")).containsExactly(identified);
    assertThat(visibleMembersOf(minted)).isEmpty();
    assertThat(visibleMembersOf(identified))
        .containsExactlyInAnyOrder(operadorIdOf(PRACE_ID), operadorIdOf(TABOADA_ID));
  }

  // And the reverse, which is the same event the awardee gate exists for: the record stops
  // publishing the identifier it published before. Minting a rival entry would leave the bid on one
  // operador and the award on another -- the procedure holding its consortium twice.
  @Test
  void consortium_the_source_stops_identifying_keeps_the_entry_it_was_catalogued_under()
      throws Exception {
    store(listing(), consortiumAwardRecord(UTE_ID));
    UUID identified = operadorIdOf(UTE_ID);

    store(listing(), consortiumAwardRecord(null));

    assertThat(rowCount("operador_economico WHERE ute")).isOne();
    assertThat(visibleBidders("822054")).containsExactly(identified);
    assertThat(awardeeOf("822054")).isEqualTo(identified);
    assertThat(wonBidders("822054")).containsExactly(identified);
  }

  // R14: absence at the source is not evidence of withdrawal, so a procedure a later run never
  // mentions is left exactly as it stands -- there is no port that could remove it.
  @Test
  void procedure_absent_from_later_import_is_retained_unchanged() throws Exception {
    store(listing(), wholeRecord());
    List<String> before = procedureColumns();

    store(listingFor("822055"), recordFor("822055"));

    assertThat(rowCount("licitacion")).isEqualTo(2);
    assertThat(procedureColumns("822054")).isEqualTo(before);
    assertThat(one("SELECT withdrawn::text AS v FROM licitacion WHERE publication_id = '822054'"))
        .isEqualTo("false");
    // And its children with it. Every withdrawal is one statement shared by six tables, so a
    // predicate that lost its licitacion_id would withdraw this procedure's whole record while
    // storing the other -- silently, and invisibly to any assertion that reads only the aggregate.
    assertThat(withdrawnChildrenOf("822054")).isZero();
  }

  // One transaction: an award naming a lote the record does not publish fails the procedure, and
  // the procedure and every child it had already written go with it.
  @Test
  void store_that_fails_part_way_writes_nothing_at_all() throws Exception {
    LicitacionRecord broken =
        recordOf(
            "Servizo de limpeza",
            List.of(lote("1")),
            List.of(award("9", EQUINSE)),
            List.of(),
            List.of(),
            List.of(cpv("45000000")),
            List.of());

    assertThatThrownBy(() -> store(listing(), broken))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(rowCount("licitacion")).isZero();
    assertThat(rowCount("licitacion_lote")).isZero();
    assertThat(rowCount("licitacion_cpv")).isZero();
  }

  // Which bid won is the resolution table's answer, and it is carried back to the bid that made it
  // -- and only to a bid, so an awardee that never bid mints nothing.
  @Test
  void the_award_is_carried_back_to_the_bid_that_won_it() throws Exception {
    store(
        listing(),
        recordOf(
            "Servizo de limpeza",
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, PLUSMER, PLUSMER_ID)),
            List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
            List.of(),
            List.of()));

    assertThat(rowCount("licitacion_participation")).isEqualTo(2);
    assertThat(wonBidders("822054")).containsExactly(operadorIdOf(EQUINSE_ID));
  }

  // The marker is set procedure by procedure. Unscoped, one import would clear every other
  // procedure's winners -- which no single-procedure test can see.
  @Test
  void marking_one_procedures_winner_leaves_another_procedures_alone() throws Exception {
    List<PublishedBidder> bidders =
        List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, PLUSMER, PLUSMER_ID));
    store(
        listing(),
        recordOf("Servizo", List.of(), List.of(award(null, EQUINSE)), bidders,
            List.of(formalisation(null, EQUINSE, EQUINSE_ID)), List.of(), List.of()));
    organoId = new OrganoId(insertOrgano("outro-organo"));

    store(
        listingFor("822055"),
        recordOf("822055", "Outro servizo", List.of(), List.of(award(null, PLUSMER)), bidders,
            List.of(formalisation(null, PLUSMER, PLUSMER_ID)), List.of(), List.of()));

    assertThat(wonBidders("822054")).containsExactly(operadorIdOf(EQUINSE_ID));
    assertThat(wonBidders("822055")).containsExactly(operadorIdOf(PLUSMER_ID));
  }

  // A bid that stops winning is cleared by the same pass, so the marker follows the resolution
  // rather than accumulating.
  @Test
  void bid_that_stops_winning_is_no_longer_marked() throws Exception {
    List<PublishedBidder> bidders =
        List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, PLUSMER, PLUSMER_ID));
    store(
        listing(),
        recordOf("Servizo", List.of(), List.of(award(null, EQUINSE)), bidders,
            List.of(formalisation(null, EQUINSE, EQUINSE_ID)), List.of(), List.of()));

    store(
        listing(),
        recordOf("Servizo", List.of(), List.of(award(null, PLUSMER)), bidders,
            List.of(formalisation(null, PLUSMER, PLUSMER_ID)), List.of(), List.of()));

    assertThat(wonBidders("822054")).containsExactly(operadorIdOf(PLUSMER_ID));
  }

  private UpsertOutcome store(LicitacionListingEntry entry, LicitacionRecord record) {
    return storeLicitacion.store(entry, record, organoId);
  }

  private static LicitacionListingEntry listing() {
    return listingFor("822054");
  }

  private static LicitacionListingEntry listingFor(String publicationId) {
    // The listing's own object and amount are deliberately unlike the record's: what is stored is
    // the record's, so that the ledger path produces the same procedure.
    return new LicitacionListingEntry(
        new PublicationId(publicationId),
        PUBLISHED_ON,
        MODIFIED_ON,
        "Como a listaxe o resume",
        new Money(new BigDecimal("1.00")),
        101,
        "Histórico");
  }

  private static LicitacionOutstandingRecord ledger() {
    return new LicitacionOutstandingRecord(
        new PublicationId("822054"), PUBLISHED_ON, MODIFIED_ON, 101, "Histórico");
  }

  /** Two lotes, two awards, a formalisation, two bidders and one row of each classification. */
  private static LicitacionRecord wholeRecord() {
    return recordFor("822054");
  }

  private static LicitacionRecord recordFor(String publicationId) {
    return recordOf(
        publicationId,
        "Servizo de limpeza",
        List.of(lote("1"), lote("2")),
        List.of(award("1", EQUINSE), award("2", PLUSMER)),
        List.of(singleFirm("1", EQUINSE, EQUINSE_ID), singleFirm("2", PLUSMER, PLUSMER_ID)),
        List.of(formalisation("1", EQUINSE, EQUINSE_ID)),
        List.of(cpv("45000000")),
        List.of(nut("ES11")));
  }

  /** The same procedure restated with one of each child gone, which is what a withdrawal is. */
  private static LicitacionRecord strippedRecord() {
    return recordOf(
        "Servizo de limpeza",
        List.of(lote("1")),
        List.of(award("1", EQUINSE)),
        List.of(singleFirm("1", EQUINSE, EQUINSE_ID)),
        List.of(),
        List.of(),
        List.of());
  }

  private static LicitacionRecord consortiumRecord(@Nullable FiscalIdentifier uteIdentifier) {
    return consortiumRecordFor(
        "822054", uteIdentifier, member(PRACE, PRACE_ID), member(TABOADA, TABOADA_ID));
  }

  private static LicitacionRecord consortiumRecordFor(
      String publicationId,
      @Nullable FiscalIdentifier uteIdentifier,
      PublishedConsortiumMember... members) {
    return recordOf(
        publicationId,
        "Obra",
        List.of(),
        List.of(),
        List.of(new PublishedBidder.Consortium(null, UTE, uteIdentifier, List.of(members))),
        List.of(),
        List.of(),
        List.of());
  }

  /** A procedure whose only content is one award, for the awardee-resolution cases. */
  private static LicitacionRecord awardOnlyRecord(
      String awardeeName, List<PublishedFormalisation> formalisations) {
    return recordOf(
        "Servizo de limpeza",
        List.of(),
        List.of(award(null, awardeeName)),
        List.of(),
        formalisations,
        List.of(),
        List.of());
  }

  /** A procedure that only catalogues its bidders, so a later name match has something to find. */
  private static LicitacionRecord biddersOnlyRecordFor(String publicationId) {
    return biddersOnlyRecordFor(publicationId, PLUSMER, PLUSMER_ID);
  }

  private static LicitacionRecord biddersOnlyRecordFor(
      String publicationId, String name, FiscalIdentifier fiscalIdentifier) {
    return recordOf(
        publicationId,
        "Outro servizo",
        List.of(),
        List.of(),
        List.of(singleFirm(null, name, fiscalIdentifier)),
        List.of(),
        List.of(),
        List.of());
  }

  /** One consortium that bid and won, so bid and award have to agree about which operador it is. */
  private static LicitacionRecord consortiumAwardRecord(
      @Nullable FiscalIdentifier uteIdentifier) {
    return recordOf(
        "Obra",
        List.of(),
        List.of(award(null, UTE)),
        List.of(
            new PublishedBidder.Consortium(
                null, UTE, uteIdentifier, List.of(member(PRACE, PRACE_ID)))),
        List.of(),
        List.of(),
        List.of());
  }

  private static LicitacionRecord recordOf(
      String obxecto,
      List<PublishedLote> lotes,
      List<PublishedAward> awards,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations,
      List<PublishedCpvClassification> cpvs,
      List<PublishedNutClassification> nuts) {
    return recordOf("822054", obxecto, lotes, awards, bidders, formalisations, cpvs, nuts);
  }

  private static LicitacionRecord recordOf(
      String publicationId,
      String obxecto,
      List<PublishedLote> lotes,
      List<PublishedAward> awards,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations,
      List<PublishedCpvClassification> cpvs,
      List<PublishedNutClassification> nuts) {
    return new LicitacionRecord(
        new PublicationId(publicationId),
        "2012/0042",
        obxecto,
        "Servizos",
        "Aberto",
        "Ordinaria",
        lotes.isEmpty() ? null : lotes.size(),
        new PublishedAmount(BUDGET, VatBasis.INCLUSIVE),
        null,
        "Histórico",
        lotes,
        awards,
        bidders,
        formalisations,
        cpvs,
        nuts);
  }

  private static PublishedLote lote(String identifier) {
    return new PublishedLote(identifier, "Lote " + identifier, null);
  }

  private static PublishedAward award(@Nullable String loteKey, String awardeeName) {
    return new PublishedAward(
        loteKey, "Adxudicado", PUBLISHED_ON, AWARDED, "12 meses", awardeeName, null);
  }

  private static PublishedBidder singleFirm(
      @Nullable String loteKey, String name, FiscalIdentifier fiscalIdentifier) {
    return new PublishedBidder.SingleFirm(loteKey, name, fiscalIdentifier);
  }

  private static PublishedConsortiumMember member(String name, FiscalIdentifier fiscalIdentifier) {
    return new PublishedConsortiumMember(name, fiscalIdentifier);
  }

  private static PublishedFormalisation formalisation(
      @Nullable String loteKey, String contratista, FiscalIdentifier fiscalIdentifier) {
    return new PublishedFormalisation(
        loteKey, PUBLISHED_ON, contratista, fiscalIdentifier, "España", AWARDED);
  }

  private static PublishedCpvClassification cpv(String code) {
    return new PublishedCpvClassification(code, null, null, PUBLISHED_ON);
  }

  private static PublishedNutClassification nut(String code) {
    return new PublishedNutClassification(code, null, null, PUBLISHED_ON);
  }

  /** Every stored value of the procedure, so two ways of storing it can be compared whole. */
  private static List<String> procedureColumns() throws SQLException {
    return procedureColumns("822054");
  }

  private static List<String> procedureColumns(String publicationId) throws SQLException {
    return row(
        "SELECT l.publication_id, l.publication_date::text, l.last_modified::text, l.expediente,"
            + " l.obxecto, l.lote_count::text, l.base_budget::text, l.estimated_value::text,"
            + " l.withdrawn::text, s.code::text, s.label"
            + " FROM licitacion l JOIN licitacion_state s ON s.id = l.state_id"
            + " WHERE l.publication_id = ?",
        11,
        publicationId);
  }

  private static String awardPathOf(String publicationId) throws SQLException {
    return one(
        "SELECT a.awardee_resolution_path AS v FROM licitacion_award a"
            + " JOIN licitacion l ON l.id = a.licitacion_id WHERE l.publication_id = ?",
        publicationId);
  }

  private static UUID awardeeOf(String publicationId) throws SQLException {
    return UUID.fromString(
        one(
            "SELECT a.operador_economico_id::text AS v FROM licitacion_award a"
                + " JOIN licitacion l ON l.id = a.licitacion_id WHERE l.publication_id = ?",
            publicationId));
  }

  /** Scoped to one procedure, so a statement that lost its own scoping is visible as a failure. */
  private static List<UUID> wonBidders(String publicationId) throws SQLException {
    return uuids(
        "SELECT p.operador_economico_id::text AS v FROM licitacion_participation p"
            + " JOIN licitacion l ON l.id = p.licitacion_id"
            + " WHERE p.won AND l.publication_id = ?",
        publicationId);
  }

  /** How many of this procedure's children carry the withdrawal marker, across all six tables. */
  private static int withdrawnChildrenOf(String publicationId) throws SQLException {
    int withdrawn = 0;
    for (String table :
        List.of(
            "licitacion_lote",
            "licitacion_cpv",
            "licitacion_nut",
            "licitacion_award",
            "licitacion_formalisation",
            "licitacion_participation")) {
      withdrawn +=
          Integer.parseInt(
              one(
                  "SELECT count(*)::text AS v FROM %s c JOIN licitacion l"
                      .formatted(table)
                      + " ON l.id = c.licitacion_id WHERE c.withdrawn AND l.publication_id = ?",
                  publicationId));
    }
    return withdrawn;
  }

  /** The operadores this procedure still visibly holds a bid for. */
  private static List<UUID> visibleBidders(String publicationId) throws SQLException {
    return uuids(
        "SELECT DISTINCT p.operador_economico_id::text AS v FROM licitacion_participation p"
            + " JOIN licitacion l ON l.id = p.licitacion_id"
            + " WHERE NOT p.withdrawn AND p.operador_economico_id IS NOT NULL"
            + " AND l.publication_id = ?",
        publicationId);
  }

  private static List<UUID> visibleMembersOf(UUID uteId) throws SQLException {
    return uuids(
        "SELECT operador_economico_id::text AS v FROM operador_ute_membership"
            + " WHERE ute_id = ? AND NOT withdrawn",
        uteId);
  }

  private static int visibleMembershipsOf(UUID operadorId) throws SQLException {
    return Integer.parseInt(
        one(
            "SELECT count(*)::text AS v FROM operador_ute_membership"
                + " WHERE operador_economico_id = ? AND NOT withdrawn",
            operadorId));
  }

  private static UUID operadorIdOf(FiscalIdentifier fiscalId) throws SQLException {
    return UUID.fromString(
        one("SELECT id::text AS v FROM operador_economico WHERE fiscal_id = ?", fiscalId.value()));
  }

  private static UUID operadorIdOfIdentifierLessUte() throws SQLException {
    return UUID.fromString(
        one("SELECT id::text AS v FROM operador_economico WHERE fiscal_id IS NULL AND ute"));
  }

  private static int rowCount(String table) throws SQLException {
    return Integer.parseInt(one("SELECT count(*)::text AS v FROM " + table));
  }

  private static int withdrawnRowCount(String table) throws SQLException {
    return Integer.parseInt(one("SELECT count(*)::text AS v FROM " + table + " WHERE withdrawn"));
  }

  private static String one(String sql, Object... arguments) throws SQLException {
    return row(sql, 1, arguments).getFirst();
  }

  private static List<String> row(String sql, int columns, Object... arguments)
      throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, arguments);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          throw new IllegalStateException("No row answered: " + sql);
        }
        List<String> values = new ArrayList<>(columns);
        for (int column = 1; column <= columns; column++) {
          values.add(rows.getString(column));
        }
        return values;
      }
    }
  }

  private static List<UUID> uuids(String sql, Object... arguments) throws SQLException {
    List<UUID> values = new ArrayList<>();
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, arguments);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          values.add(UUID.fromString(rows.getString("v")));
        }
      }
    }
    return values;
  }

  private static UUID insertOrgano(String sourceKey) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "INSERT INTO organo_contratacion (id, source_key, name, active)"
                    + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id")) {
      statement.setObject(1, sourceKey);
      statement.setObject(2, "Axencia Turismo de Galicia");
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getObject("id", UUID.class);
      }
    }
  }

  private static void bind(PreparedStatement statement, Object... arguments) throws SQLException {
    for (int argument = 0; argument < arguments.length; argument++) {
      statement.setObject(argument + 1, arguments[argument]);
    }
  }

  // Off the container rather than the injected DataSource: with no ambient transaction every write
  // under test really commits, and these assertions are about what it committed.
  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
