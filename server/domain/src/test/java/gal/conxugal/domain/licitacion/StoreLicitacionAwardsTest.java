package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

  // An identifier-less row is still a party the source published under that name, so it is the
  // ambiguity B exists to refuse — taking the other row's identifier would attribute the award to a
  // party that may not be the awardee, and mark it published rather than derived.
  @Test
  void second_bidder_of_the_same_name_publishing_no_identifier_declines_path_bid() {
    theStoreAcceptsEveryAward();
    theCatalogueHoldsNobodyNamed();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(),
            List.of(singleFirm(null, EQUINSE, EQUINSE_ID), singleFirm(null, EQUINSE, null)));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isNull();
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.UNRESOLVED);
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

  // The contratista's name where the resolution published none: without the fallback the operador
  // would be catalogued as the empty string, at this procedure's own rank — displayed as nothing
  // until a later contract outranked it, and then filed among the names it has borne.
  @Test
  void formalisation_supplies_the_name_where_the_award_row_published_none() {
    theStoreCataloguesOnDemand();

    store(
        List.of(),
        List.of(award(null, null)),
        List.of(formalisation(null, EQUINSE, EQUINSE_ID)),
        List.of());

    assertThat(catalogued(EQUINSE_ID).name()).isEqualTo(EQUINSE);
  }

  // Nothing guarantees the parse emits one formalisation per award point, and the store upserts
  // them on it — so a row that answers nothing is not evidence against the row beside it.
  @Test
  void second_formalisation_of_one_award_point_answers_where_the_first_published_no_identifier() {
    theStoreCataloguesOnDemand();

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, EQUINSE)),
            List.of(formalisation(null, EQUINSE, null), formalisation(null, EQUINSE, EQUINSE_ID)),
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
  // identifier is published and therefore evidence the cataloguing needs whole. A caller that has
  // not catalogued the consortia leaves the award naming nobody rather than matching its name.
  @Test
  void award_whose_awardee_is_consortium_row_takes_no_route_when_none_were_catalogued() {
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

  // The award belongs to the consortium's own operador and to no member of it, which is the
  // storage form of no euro being counted twice. The routes are what this asserts against rather
  // than the link alone: a formalisation naming the same party carries an identifier, and taking
  // it would attribute the award to a second operador while the bid pointed at the first — the
  // shape SPEC-0006 #40 forbids. An unidentified consortium has nothing to resolve through, so the
  // link is direct, the catalogue match is never asked, and the catalogue is left as its bid made
  // it.
  @Test
  void award_to_unidentified_consortium_is_held_by_its_operador_and_takes_no_other_route() {
    theStoreAcceptsEveryAward();
    OperadorId ute = new OperadorId(UUID.randomUUID());

    List<Award> stored =
        store(
            List.of(),
            List.of(award(null, "UTE PRACE-TABOADA RAMOS")),
            List.of(formalisation(null, "UTE PRACE-TABOADA RAMOS", EQUINSE_ID)),
            List.of(consortium(null, "UTE PRACE-TABOADA RAMOS")),
            wasCatalogued(
                "UTE PRACE-TABOADA RAMOS", ute, null, AwardeeResolutionPath.PUBLISHED_BY_BIDDER));

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(ute);
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
            });
    assertThat(catalogue).isEmpty();
    verify(operadores, never()).findAllMatchingName(any());
    verify(operadores, never()).insert(any());
  }

  // An identified consortium is an ordinary awardee: the award resolves through the catalogue on
  // the identifier the procedure published, so it ranks that consortium's name as any contract
  // does — the split TASK-0022 draws between a bid, which ranks nothing, and the award it won.
  @Test
  void award_to_identified_consortium_resolves_through_the_catalogue_and_ranks_its_name() {
    theStoreCataloguesOnDemand();
    FiscalIdentifier uteId = new FiscalIdentifier("U88779475");

    store(
        List.of(),
        List.of(award(null, "UTE INSIDE OVIGA RIBADEO")),
        List.of(),
        List.of(consortium(null, "UTE INSIDE OVIGA RIBADEO")),
        wasCatalogued(
            "UTE INSIDE OVIGA RIBADEO",
            new OperadorId(UUID.randomUUID()),
            uteId,
            AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION));

    assertThat(catalogued(uteId))
        .satisfies(
            operador -> {
              assertThat(operador.name()).isEqualTo("UTE INSIDE OVIGA RIBADEO");
              assertThat(operador.nameRank()).isEqualTo(new NomeRank(PUBLISHED_ON, 822054L));
            });
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
                List.of(),
                ConsortiumOperadores.none());

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

  // The mechanism behind the historical tail closing: a procedure moves from adxudicado to
  // formalizado, and the run that re-reads it replaces a name-derived link with a published one —
  // even where that names a different operador, because the source now says so.
  @Test
  void formalising_supersedes_name_derived_link_even_for_another_operador() {
    theStoreCataloguesOnDemand();
    OperadorEconomico guessed = catalogueHolds(XESTION_ID, XESTION);
    theStoreAlreadyHolds(
        storedAward(null, guessed.id(), AwardeeResolutionPath.NAME_DERIVED));

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

  // The gate, and the reason it outranks the refresh rule for this one field: a formalisation
  // withdrawn at the source is not evidence that the identifier it published was wrong.
  @Test
  void formalisation_disappearing_leaves_the_published_link_it_justified_standing() {
    theStoreAcceptsEveryAward();
    OperadorEconomico published = catalogueHolds(EQUINSE_ID, EQUINSE);
    theStoreAlreadyHolds(
        storedAward(null, published.id(), AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION));

    List<Award> stored = store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.operadorEconomicoId()).isEqualTo(published.id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
            });
  }

  // The same rule one step down the order: a bid published the identifier, and a later record that
  // can only match a name does not get to replace it with an inference.
  @Test
  void name_derived_route_does_not_replace_link_the_bidder_list_published() {
    theStoreAcceptsEveryAward();
    OperadorEconomico published = catalogueHolds(EQUINSE_ID, EQUINSE);
    theStoreAlreadyHolds(
        storedAward(null, published.id(), AwardeeResolutionPath.PUBLISHED_BY_BIDDER));

    List<Award> stored = store(List.of(), List.of(award(null, EQUINSE)), List.of(), List.of());

    assertThat(stored)
        .singleElement()
        .satisfies(
            award ->
                assertThat(award.awardeeResolutionPath())
                    .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER));
  }

  // A refused write catalogues nothing and promotes no name: the gate is applied before the
  // operador is reached, not after, so nothing is written on the strength of a link nothing holds.
  @Test
  void refused_write_reaches_no_operador_at_all() {
    theStoreAcceptsEveryAward();
    OperadorEconomico published = catalogueHolds(EQUINSE_ID, EQUINSE);
    theStoreAlreadyHolds(
        storedAward(null, published.id(), AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION));

    store(
        List.of(),
        List.of(award(null, EQUINSE)),
        List.of(),
        List.of(singleFirm(null, EQUINSE, EQUINSE_ID)));

    verify(operadores, never()).insert(any());
    verify(operadores, never()).promoteName(any(), any(), any());
  }

  // The everyday case, and the one an over-eager gate would break: nothing has changed, so the
  // award is written exactly as it stands rather than being frozen by its own stored route.
  @Test
  void an_unchanged_restatement_writes_the_same_link_by_the_same_route() {
    theStoreAcceptsEveryAward();
    theCatalogueAnswers();
    OperadorEconomico published = catalogueHolds(EQUINSE_ID, EQUINSE);
    theStoreAlreadyHolds(
        storedAward(null, published.id(), AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION));

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
              assertThat(award.operadorEconomicoId()).isEqualTo(published.id());
              assertThat(award.awardeeResolutionPath())
                  .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
            });
  }

  /** An operador the catalogue already holds, as a route reaching its identifier would find it. */
  private OperadorEconomico catalogueHolds(FiscalIdentifier fiscalId, String name) {
    OperadorEconomico operador =
        new OperadorEconomico(
            new OperadorId(UUID.randomUUID()),
            fiscalId,
            name,
            false,
            new NomeRank(PUBLISHED_ON, 822054L),
            Set.of());
    catalogue.put(fiscalId, operador);
    return operador;
  }

  private void theStoreAlreadyHolds(Award award) {
    when(awards.findAllByLicitacionId(LICITACION_ID)).thenReturn(List.of(award));
  }

  /** An award as an earlier import left it: at an award point, naming somebody, by some route. */
  private static Award storedAward(
      @Nullable LoteId loteId, @Nullable OperadorId operadorId, AwardeeResolutionPath path) {
    return new Award(
        new AwardId(UUID.randomUUID()),
        LICITACION_ID,
        loteId,
        null,
        null,
        AWARDED,
        null,
        EQUINSE,
        operadorId,
        path,
        false);
  }

  private List<Award> store(
      List<Lote> lotes,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders) {
    return store(lotes, published, formalisations, bidders, ConsortiumOperadores.none());
  }

  private List<Award> store(
      List<Lote> lotes,
      List<PublishedAward> published,
      List<PublishedFormalisation> formalisations,
      List<PublishedBidder> bidders,
      ConsortiumOperadores consortia) {
    return new StoreLicitacionAwards(new ResolveOperador(operadores), operadores, awards)
        .store(licitacion(PUBLICATION_ID), lotes, published, formalisations, bidders, consortia);
  }

  private OperadorEconomico catalogued(FiscalIdentifier fiscalId) {
    return catalogue.get(fiscalId);
  }

  /** One consortium of the procedure, as the store that catalogued it answered. */
  private static ConsortiumOperadores wasCatalogued(
      String publishedName,
      OperadorId operadorId,
      @Nullable FiscalIdentifier fiscalId,
      AwardeeResolutionPath path) {
    return new ConsortiumOperadores(
        Map.of(
            MatchableName.of(publishedName).orElseThrow(),
            new ConsortiumOperadores.CataloguedConsortium(operadorId, fiscalId, path)));
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

  /** The catalogue as it stands, for a route that only ever looks an identifier up. */
  private void theCatalogueAnswers() {
    when(operadores.findByFiscalId(any()))
        .thenAnswer(
            invocation ->
                Optional.ofNullable(catalogue.get(invocation.<FiscalIdentifier>getArgument(0))));
  }

  /**
   * A catalogue an award can add to, shared across the whole call as it would be inside the use
   * case's transaction, so a second award naming the same firm reads what the first one wrote.
   */
  private void theStoreCataloguesOnDemand() {
    theStoreAcceptsEveryAward();
    theCatalogueAnswers();
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
