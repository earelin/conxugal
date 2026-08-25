package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.MatchableName;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.ResolveOperador;
import gal.conxugal.domain.organo.OrganoId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which operador an award reaches, by which of the three routes, and what an award that reaches
 * none is stored as.
 *
 * <p>The real {@link ResolveOperador} is wired rather than stubbed, on
 * {@link StoreLicitacionBiddersTest}'s reasoning: what these assert on is the routing into it and
 * what comes back out, and a stub of the collaborator would let the routing agree with itself.
 * What <em>is</em> stubbed is the catalogue underneath it, so that a route's effect on the
 * catalogue — above all path C's promise to have none — is visible as state rather than as a call.
 */
@ExtendWith(MockitoExtension.class)
class StoreLicitacionAwardsTest {

  private static final LicitacionId LICITACION_ID = new LicitacionId(UUID.randomUUID());
  private static final PublicationId PUBLICATION_ID = new PublicationId("822054");
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2024, 7, 10);
  private static final FiscalIdentifier EQUINSE_ID = new FiscalIdentifier("A41111220");
  private static final FiscalIdentifier XESTION_ID = new FiscalIdentifier("B15112222");
  private static final String EQUINSE = "EQUINSE, S.A.";
  private static final String XESTION = "Xestión Ambiental de Contratas, S.L.";
  private static final Money AWARDED = new Money(new BigDecimal("206996.66"));

  @Mock
  private OperadorRepository operadores;

  @Mock
  private AwardRepository awards;

  private final Map<FiscalIdentifier, OperadorEconomico> catalogue = new HashMap<>();

  // Path A, and the 58% of award rows it answers for: the Contratista cell publishes the name and
  // the identifier together, which is reading the record rather than guessing at it.
  @Test
  void formalisation_publishing_the_identifier_resolves_the_award_to_that_operador() {
    theStoreCataloguesOnDemand();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
            List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued(EQUINSE_ID).id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
            });
  }

  // The tables spell one lote differently — 05 against 5 was measured within a single record — so
  // the naive join would demote a published route to a derived one on every padded lote.
  @Test
  void formalisation_writing_the_padded_lote_still_answers_for_the_award_row_that_did_not() {
    theStoreCataloguesOnDemand();
    Lote lote = storedLote("1");

    List<Award> stored =
        store(
            List.of(lote),
            List.of(award("1", EQUINSE)),
            List.of(formalisation("01", EQUINSE, EQUINSE_ID)),
            List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.loteId()).isEqualTo(lote.id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
            });
  }

  // The resolution states who was awarded; a formalisation naming someone else is a fact about
  // signing, and attributing the award to that party would put money against an operador the
  // source never awarded it to.
  @Test
  void formalisation_naming_another_party_leaves_the_award_attributed_to_the_resolutions() {
    theStoreCataloguesOnDemand();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(formalisation(null, XESTION, XESTION_ID)),
            List.of(singleFirm(null, EQUINSE, EQUINSE_ID)));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued(EQUINSE_ID).id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
            });
    assertThat(catalogue).doesNotContainKey(XESTION_ID);
  }

  @Test
  void awardee_matching_one_bidder_row_resolves_from_the_identifier_that_bidder_published() {
    theStoreCataloguesOnDemand();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(),
            List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, XESTION, XESTION_ID)));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued(EQUINSE_ID).id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
            });
  }

  // Path B is path C's comparison scoped to one procedure, so an ambiguous B is no more usable
  // than an ambiguous C: two parties published under one name answer nobody, and C is asked next.
  @Test
  void two_bidders_publishing_the_same_name_decline_and_the_catalogue_is_asked_instead() {
    theStoreAcceptsEveryAward();
    OperadorId catalogued = theCatalogueHoldsOneOperadorNamed();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(),
            List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, EQUINSE, XESTION_ID)));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued);
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.NAME_DERIVED);
            });
  }

  // One firm bidding at two award points is two rows and one party, which is the ordinary shape of
  // a procedure with lotes — so the uniqueness is over what those rows published, not over them.
  @Test
  void one_firm_bidding_at_two_award_points_is_one_match_rather_than_an_ambiguity() {
    theStoreCataloguesOnDemand();
    Lote first = storedLote("1");
    Lote second = storedLote("2");

    List<Award> stored =
        store(
            List.of(first, second),
            List.of(award("1", EQUINSE)),
            List.of(),
            List.of(singleFirm("1", EQUINSE, EQUINSE_ID), singleFirm("2", EQUINSE, EQUINSE_ID)));

    assertThat(stored)
        .singleElement()
        .extracting(Award::awardeeResolutionPath)
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
  }

  @Test
  void awardee_matching_exactly_one_catalogued_operador_resolves_and_is_marked_derived() {
    theStoreAcceptsEveryAward();
    OperadorId catalogued = theCatalogueHoldsOneOperadorNamed();

    List<Award> stored =
        store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued);
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.NAME_DERIVED);
            });
  }

  // The failure this bounds is silent and permanent: an invented operador is what SPEC-0006 R5
  // forbids, and no import undoes one. Asserted as catalogue contents rather than as a call.
  @Test
  void catalogue_match_that_finds_nobody_leaves_the_award_naming_nobody_and_creates_nothing() {
    theStoreAcceptsEveryAward();
    theCatalogueHoldsNobodyNamed();

    List<Award> stored =
        store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isNull();
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.UNRESOLVED);
            });
    assertThat(catalogue).isEmpty();
  }

  // Measured ambiguity is 1 name in 268, and that one is a source typo — so the award is stored
  // naming nobody rather than attributed to whichever of the two the query answered first.
  @Test
  void awardee_matching_two_catalogued_operadores_leaves_the_award_naming_nobody() {
    theStoreAcceptsEveryAward();
    theCatalogueHolds(new OperadorId(UUID.randomUUID()), new OperadorId(UUID.randomUUID()));

    List<Award> stored =
        store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isNull();
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.UNRESOLVED);
            });
  }

  // The comparison folds; the storage does not. R33 holds every value as published, so what folded
  // for the match is not what is written down.
  @Test
  void matching_folds_case_accents_and_punctuation_and_stores_the_name_as_published() {
    theStoreAcceptsEveryAward();
    OperadorId catalogued = new OperadorId(UUID.randomUUID());
    theCatalogueHoldsOneOperadorNamed("XESTION AMBIENTAL DE CONTRATAS SL", catalogued);

    List<Award> stored =
        store(List.of(), List.of(award(null, XESTION)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(catalogued);
              assertThat(award.awardeeName()).isEqualTo(XESTION);
            });
  }

  // Whether the consortium is catalogued is a property of the procedure rather than of this row,
  // so no route is tried at all — including the formalisation, which is one of the two places its
  // identifier is published and therefore evidence the task that attributes it needs whole.
  @Test
  void award_whose_awardee_is_consortium_row_takes_no_route_and_links_nobody() {
    theStoreAcceptsEveryAward();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, "UTE PRACE-TABOADA RAMOS")),
            List.of(
                formalisation(null, "UTE PRACE-TABOADA RAMOS", new FiscalIdentifier("U8648666"))),
            List.of(consortium(null, "UTE PRACE-TABOADA RAMOS")));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isNull();
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.UNRESOLVED);
            });
    assertThat(catalogue).isEmpty();
  }

  // R16 and R25 make this an outcome rather than a failure: the award is stored, the procedure
  // stays visible, and the path says so rather than leaving a reader to infer it from a null.
  @Test
  void award_no_route_reaches_is_stored_holding_no_operador_and_states_that_as_its_path() {
    theStoreAcceptsEveryAward();
    theCatalogueHoldsNobodyNamed();

    List<Award> stored =
        store(
            List.of(storedLote("1"), storedLote("2")),
            List.of(award("1", null), award("2", EQUINSE)),
            List.of(),
            List.of());

    assertThat(stored)
        .hasSize(2)
        .allSatisfy(
            award ->
                assertThat(award.awardeeResolutionPath())
                    .isEqualTo(AwardeeResolutionPath.UNRESOLVED));
  }

  // TASK-0021's answer, at its first caller: text order is not numeric order, so an identifier
  // that is not a number ranks nothing rather than ranking as text and putting "9" above "10".
  @Test
  void procedure_whose_publication_identifier_is_not_number_resolves_but_ranks_nothing() {
    theStoreCataloguesOnDemand();

    List<Award> stored =
        new StoreLicitacionAwards(new ResolveOperador(operadores), operadores, awards)
            .store(
                licitacion(new PublicationId("LIC-2024-0007")),
                List.of(),
                List.of(award(null, EQUINSE)),
                List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
                List.of());

    assertThat(stored)
        .singleElement()
        .extracting(Award::awardeeResolutionPath)
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
    assertThat(catalogued(EQUINSE_ID).nameRank()).isEqualTo(NomeRank.unranked());
  }

  @Test
  void resolved_award_ranks_its_operador_name_at_the_procedures_publication() {
    theStoreCataloguesOnDemand();

    store(
        List.of(),
        List.of(award(null, EQUINSE)),
        List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
        List.of());

    assertThat(catalogued(EQUINSE_ID).nameRank())
        .isEqualTo(new NomeRank(PUBLISHED_ON, 822054L));
  }

  @Test
  void award_carries_every_value_its_published_row_did() {
    theStoreAcceptsEveryAward();
    theCatalogueHoldsNobodyNamed();
    Lote lote = storedLote("1");

    List<Award> stored =
        store(List.of(lote), List.of(award("1", EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.licitacionId()).isEqualTo(LICITACION_ID);
              assertThat(award.loteId()).isEqualTo(lote.id());
              assertThat(award.resolution()).isEqualTo("Adxudicado");
              assertThat(award.resolutionDate()).isEqualTo(PUBLISHED_ON);
              assertThat(award.amount()).isEqualTo(AWARDED);
              assertThat(award.executionPeriod()).isEqualTo("12 meses");
              assertThat(award.withdrawn()).isFalse();
            });
  }

  private List<Award> store(
      List<Lote> lotes,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders) {
    return new StoreLicitacionAwards(new ResolveOperador(operadores), operadores, awards)
        .store(licitacion(PUBLICATION_ID), lotes, published, formalisations, bidders);
  }

  private OperadorEconomico catalogued(FiscalIdentifier fiscalId) {
    return catalogue.get(fiscalId);
  }

  private static Licitacion licitacion(PublicationId publicationId) {
    return new Licitacion(
        LICITACION_ID,
        publicationId,
        new OrganoId(UUID.randomUUID()),
        PUBLISHED_ON,
        PUBLISHED_ON,
        new LicitacionState(new LicitacionStateId(UUID.randomUUID()), 2, "Adxudicado"),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false);
  }

  private static PublishedAward award(@Nullable String loteKey, @Nullable String awardeeName) {
    return new PublishedAward(
        loteKey, "Adxudicado", PUBLISHED_ON, AWARDED, "12 meses", awardeeName, null);
  }

  private static PublishedFormalisation formalisation(
      @Nullable String loteKey, String contratista, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedFormalisation(
        loteKey, PUBLISHED_ON, contratista, fiscalIdentifier, "España", AWARDED);
  }

  private static PublishedBidder singleFirm(
      @Nullable String loteKey, String name, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedBidder.SingleFirm(loteKey, name, fiscalIdentifier);
  }

  private static PublishedBidder consortium(@Nullable String loteKey, String name) {
    return new PublishedBidder.Consortium(
        loteKey,
        name,
        null,
        List.of(new PublishedConsortiumMember("PRACE SERVICIOS Y OBRAS SA", EQUINSE_ID)));
  }

  /** A lote as the procedure's own store answered it, carrying the identity an award hangs off. */
  private static Lote storedLote(String identifier) {
    return new Lote(new LoteId(UUID.randomUUID()), LICITACION_ID, identifier, null, null, false);
  }

  /** The one operador the catalogue answers with, whatever name it is asked about. */
  private OperadorId theCatalogueHoldsOneOperadorNamed() {
    OperadorId catalogued = new OperadorId(UUID.randomUUID());
    theCatalogueHolds(catalogued);
    return catalogued;
  }

  /**
   * A catalogue that answers for one name and no other, so the fold is what the match rests on: a
   * comparison that stopped folding accents or punctuation would ask about a key this declines.
   */
  private void theCatalogueHoldsOneOperadorNamed(String name, OperadorId catalogued) {
    when(operadores.findAllMatchingName(MatchableName.of(name).orElseThrow()))
        .thenReturn(Set.of(catalogued));
  }

  private void theCatalogueHoldsNobodyNamed() {
    theCatalogueHolds();
  }

  private void theCatalogueHolds(OperadorId... matching) {
    when(operadores.findAllMatchingName(any())).thenReturn(Set.of(matching));
  }

  /** The award store, assigning the identity the database would on insert. */
  private void theStoreAcceptsEveryAward() {
    when(awards.upsert(any()))
        .thenAnswer(
            invocation -> {
              Award incoming = invocation.getArgument(0);
              return new Award(
                  new AwardId(UUID.randomUUID()),
                  incoming.licitacionId(),
                  incoming.loteId(),
                  incoming.resolution(),
                  incoming.resolutionDate(),
                  incoming.amount(),
                  incoming.executionPeriod(),
                  incoming.awardeeName(),
                  incoming.operadorEconomicoId(),
                  incoming.awardeeResolutionPath(),
                  incoming.withdrawn());
            });
  }

  /**
   * A catalogue an award can add to, shared across the whole call as it would be inside the use
   * case's transaction, so a second award naming the same firm reads what the first one wrote.
   */
  private void theStoreCataloguesOnDemand() {
    theStoreAcceptsEveryAward();
    when(operadores.findByFiscalId(any()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(catalogue.get(invocation.<FiscalIdentifier>getArgument(0))));
    when(operadores.insert(any()))
        .thenAnswer(
            invocation -> {
              OperadorEconomico incoming = invocation.getArgument(0);
              OperadorEconomico stored =
                  new OperadorEconomico(
                      new OperadorId(UUID.randomUUID()),
                      incoming.fiscalId(),
                      incoming.name(),
                      false,
                      incoming.nameRank(),
                      Set.of());
              catalogue.put(stored.fiscalId(), stored);
              return stored;
            });
  }
}
