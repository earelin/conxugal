package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
import gal.conxugal.domain.operador.UteMembership;
import gal.conxugal.domain.operador.UteMembershipRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which operador a consortium becomes, where its identifier is taken from, and what its membership
 * is stored as.
 *
 * <p>The real {@link ResolveOperador} is wired rather than stubbed, on
 * {@link StoreLicitacionBiddersTest}'s reasoning: what these assert on is the routing into it and
 * what the catalogue holds afterwards, and a stub of the collaborator would let the routing agree
 * with itself.
 */
@ExtendWith(MockitoExtension.class)
class StoreLicitacionConsortiaTest {

  private static final LicitacionId LICITACION_ID = new LicitacionId(UUID.randomUUID());
  private static final String UTE = "UTE PRACE-TABOADA RAMOS";
  private static final FiscalIdentifier UTE_ID = new FiscalIdentifier("U88779475");
  private static final FiscalIdentifier PRACE_ID = new FiscalIdentifier("A70319678");
  private static final FiscalIdentifier TABOADA_ID = new FiscalIdentifier("B94181807");
  private static final String PRACE = "PRACE SERVICIOS Y OBRAS SA";
  private static final String TABOADA = "CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU";

  @Mock
  private OperadorRepository operadores;

  @Mock
  private ParticipationRepository participations;

  @Mock
  private UteMembershipRepository memberships;

  private final Map<FiscalIdentifier, OperadorEconomico> catalogue = new HashMap<>();

  // The 2-of-35 branch: the bidder row publishes the consortium's own identifier, so it is an
  // ordinary catalogue entry that carries the marker — and the same operador on the next
  // procedure that names it.
  @Test
  void consortium_publishing_its_identifier_is_catalogued_under_it_and_marked() {
    theStoreCataloguesOnDemand();

    ConsortiumOperadores catalogued =
        store(List.of(consortium(null, UTE, UTE_ID, member(PRACE, PRACE_ID))), List.of());

    assertThat(cataloguedUnder(UTE_ID))
        .satisfies(
            operador -> {
              assertThat(operador.name()).isEqualTo(UTE);
              assertThat(operador.ute()).isTrue();
              assertThat(operador.nameRank()).isEqualTo(NomeRank.unranked());
            });
    assertThat(catalogued.at(matchable(UTE)))
        .satisfies(
            consortium -> {
              assertThat(consortium.fiscalId()).isEqualTo(UTE_ID);
              assertThat(consortium.path()).isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_BIDDER);
            });
  }

  // R17's "anywhere on the procedure" is load-bearing: the formalisation identifies consortia the
  // bidder row does not, and taking the bidder row alone would mint an identifier-less entry that
  // the formalisation then had to merge away — the retro-active re-partition ADR-0023 forbids.
  @Test
  void consortium_the_formalisation_identifies_is_catalogued_under_that_identifier() {
    theStoreCataloguesOnDemand();

    ConsortiumOperadores catalogued =
        store(
            List.of(consortium(null, UTE, null, member(PRACE, PRACE_ID))),
            List.of(formalisation(null, UTE, UTE_ID)));

    assertThat(cataloguedUnder(UTE_ID)).extracting(OperadorEconomico::ute).isEqualTo(true);
    assertThat(catalogued.at(matchable(UTE)).path())
        .isEqualTo(AwardeeResolutionPath.PUBLISHED_BY_FORMALISATION);
  }

  // The failing shape this rules out is a procedure holding its consortium twice — once on its
  // bid and once on its award — which would leave one of the two with an award and no members.
  @Test
  void consortium_the_formalisation_identifies_mints_no_identifier_less_row_at_all() {
    theStoreCataloguesOnDemand();

    store(
        List.of(consortium(null, UTE, null, member(PRACE, PRACE_ID))),
        List.of(formalisation(null, UTE, UTE_ID)));

    assertThat(theOperadoresCatalogued())
        .allSatisfy(operador -> assertThat(operador.fiscalId()).isNotNull());
    verify(participations, never()).findConsortiumOperador(any(), any());
  }

  // A formalisation naming some other party is evidence about that party. Taking its identifier
  // would catalogue this consortium as a firm it has nothing to do with.
  @Test
  void formalisation_naming_another_party_does_not_identify_this_consortium() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores catalogued =
        store(
            List.of(consortium(null, UTE, null, member(PRACE, PRACE_ID))),
            List.of(formalisation(null, "EQUINSE, S.A.", new FiscalIdentifier("A41111220"))));

    assertThat(catalogued.at(matchable(UTE)).fiscalId()).isNull();
  }

  // The 33-of-35 branch: nothing is invented, the placeholder the NIF cell carried never becomes
  // an identity, and the entry holds the name its bid published.
  @Test
  void consortium_no_publication_identifies_is_minted_holding_no_identifier() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores catalogued =
        store(List.of(consortium(null, UTE, null, member(PRACE, PRACE_ID))), List.of());

    assertThat(theOperadoresCatalogued())
        .filteredOn(operador -> operador.fiscalId() == null)
        .singleElement()
        .satisfies(
            operador -> {
              assertThat(operador.name()).isEqualTo(UTE);
              assertThat(operador.ute()).isTrue();
              assertThat(operador.nameRank()).isEqualTo(NomeRank.unranked());
            });
    assertThat(catalogued.at(matchable(UTE)).fiscalId()).isNull();
  }

  // The problem TASK-0006 left open: such an entry answers to nothing inside the catalogue, so a
  // re-import would mint a second and the procedure would hold its consortium twice.
  @Test
  void re_import_finds_the_identifier_less_consortium_its_own_bid_named() {
    theStoreAcceptsEveryBid();
    OperadorId minted = new OperadorId(UUID.randomUUID());
    when(participations.findConsortiumOperador(LICITACION_ID, matchable(UTE)))
        .thenReturn(Optional.of(minted));

    ConsortiumOperadores catalogued = store(List.of(consortium(null, UTE, null)), List.of());

    assertThat(catalogued.at(matchable(UTE)).operadorId()).isEqualTo(minted);
    verify(operadores, never()).insert(any());
  }

  // The lookup folds case, accents and punctuation, so a procedure restated with the consortium's
  // name repunctuated finds the entry it minted rather than minting a second beside it.
  @Test
  void restated_consortium_name_is_looked_up_on_the_same_fold_every_other_comparison_uses() {
    theStoreAcceptsEveryBid();
    when(participations.findConsortiumOperador(any(), any()))
        .thenReturn(Optional.of(new OperadorId(UUID.randomUUID())));

    store(List.of(consortium(null, "UTE Prace — Taboada, Ramos", null)), List.of());

    verify(participations).findConsortiumOperador(LICITACION_ID, matchable(UTE));
  }

  // Every member is an operador under its own published identifier, and the membership relates the
  // two operadores rather than hanging off the bid — which is what lets it read from either end.
  @Test
  void every_member_becomes_an_operador_related_to_the_consortium() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores catalogued =
        store(
            List.of(
                consortium(null, UTE, null, member(PRACE, PRACE_ID), member(TABOADA, TABOADA_ID))),
            List.of());

    OperadorId ute = catalogued.at(matchable(UTE)).operadorId();
    assertThat(theMembershipsStored())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new UteMembership(ute, cataloguedUnder(PRACE_ID).id(), LICITACION_ID),
            new UteMembership(ute, cataloguedUnder(TABOADA_ID).id(), LICITACION_ID));
  }

  // One shape, two branches: the identified consortium's membership is the unidentified one's.
  @Test
  void identified_and_unidentified_consortium_produce_the_same_membership_rows() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores identified =
        store(List.of(consortium(null, UTE, UTE_ID, member(PRACE, PRACE_ID))), List.of());
    ConsortiumOperadores unidentified =
        store(
            List.of(consortium(null, "MISTURAS-INGESAN", null, member(PRACE, PRACE_ID))),
            List.of());

    assertThat(theMembershipsStored())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new UteMembership(
                identified.at(matchable(UTE)).operadorId(),
                cataloguedUnder(PRACE_ID).id(),
                LICITACION_ID),
            new UteMembership(unidentified.at(matchable("MISTURAS-INGESAN")).operadorId(),
                cataloguedUnder(PRACE_ID).id(), LICITACION_ID));
  }

  // R16's rule reaches a member: it yields no operador and no membership, and costs the
  // consortium and its other members nothing.
  @Test
  void member_whose_identifier_is_unusable_yields_no_membership_and_costs_the_rest_nothing() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores catalogued =
        store(
            List.of(
                consortium(
                    null, UTE, null, member("Sen NIF SL", null), member(TABOADA, TABOADA_ID))),
            List.of());

    assertThat(theMembershipsStored())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new UteMembership(
                catalogued.at(matchable(UTE)).operadorId(),
                cataloguedUnder(TABOADA_ID).id(),
                LICITACION_ID));
  }

  // A UTE is constituted for a procedure, so bidding on two of its lotes is one catalogue entry
  // with two bids — not two entries splitting its members and its awards between them.
  @Test
  void consortium_bidding_on_two_lotes_is_one_operador_with_one_bid_at_each() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();
    Lote first = storedLote("1");
    Lote second = storedLote("2");

    ConsortiumOperadores catalogued =
        store(
            List.of(first, second),
            List.of(
                consortium("1", UTE, null, member(PRACE, PRACE_ID)),
                consortium("2", UTE, null, member(PRACE, PRACE_ID))),
            List.of());

    OperadorId ute = catalogued.at(matchable(UTE)).operadorId();
    assertThat(theBidsStored())
        .extracting(Participation::loteId)
        .containsExactly(first.id(), second.id());
    assertThat(theBidsStored()).extracting(Participation::operadorEconomicoId).containsOnly(ute);
  }

  // The bids are handed back rather than reconciled here, because they are half of one table
  // StoreLicitacionBidders writes the other half of. A caller that could not see them would have to
  // reconcile half a table against itself and withdraw every single-firm bid on the procedure.
  //
  // What it hands back is the *stored* bid rather than the one passed in, which is the half that
  // matters: reconciliation withdraws by identity, and the identity is what the store assigns.
  @Test
  void answers_the_stored_bids_so_the_bidder_table_is_reconciled_once() {
    theStoreCataloguesOnDemand();
    Lote first = storedLote("1");
    Lote second = storedLote("2");

    StoredConsortia stored =
        stored(
            List.of(first, second),
            List.of(
                consortium("1", UTE, null, member(PRACE, PRACE_ID)),
                consortium("2", UTE, null, member(PRACE, PRACE_ID))),
            List.of());

    assertThat(stored.bids())
        .extracting(Participation::loteId)
        .containsExactly(first.id(), second.id());
    assertThat(stored.bids()).allSatisfy(bid -> assertThat(bid.id()).isNotNull());
  }

  // The distinct-ends CHECK and UteMembership's own constructor both refuse a consortium that is
  // its own member, and either would abort the whole procedure's import rather than lose one row.
  // The guard is what keeps a member republishing the consortium's identifier from reaching them.
  @Test
  void member_publishing_the_consortiums_own_identifier_is_not_related_to_it() {
    theStoreCataloguesOnDemand();

    ConsortiumOperadores catalogued =
        store(
            List.of(
                consortium(
                    null, UTE, UTE_ID, member(UTE, UTE_ID), member(TABOADA, TABOADA_ID))),
            List.of());

    assertThat(theMembershipsStored())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new UteMembership(
                catalogued.at(matchable(UTE)).operadorId(),
                cataloguedUnder(TABOADA_ID).id(),
                LICITACION_ID));
  }

  // R16 records every operador that applied and SPEC-0008 #19 requires every published bidder to
  // be stored, with no exception for a consortium — so a row with no name to be catalogued under
  // still leaves its bid, naming nobody, exactly as an unusable identifier does for a single firm.
  @Test
  void consortium_published_under_no_name_stores_its_bid_naming_nobody() {
    theStoreAcceptsEveryBid();

    ConsortiumOperadores catalogued =
        store(List.of(consortium(null, null, null, member(PRACE, PRACE_ID))), List.of());

    assertThat(theBidsStored())
        .singleElement()
        .satisfies(
            bid -> {
              assertThat(bid.licitacionId()).isEqualTo(LICITACION_ID);
              assertThat(bid.operadorEconomicoId()).isNull();
            });
    assertThat(catalogued.byName()).isEmpty();
    verify(operadores, never()).insert(any());
    verify(memberships, never()).upsert(any());
  }

  // The published name is the only thing telling one bid's consortium from another's inside one
  // procedure, so a row whose name folds to nothing is keyed by nothing either — the fold refuses
  // it for the same reason the catalogue match does.
  @Test
  void consortium_whose_name_folds_to_nothing_is_treated_as_publishing_none() {
    theStoreAcceptsEveryBid();

    ConsortiumOperadores catalogued =
        store(List.of(consortium(null, "-", null, member(PRACE, PRACE_ID))), List.of());

    assertThat(theBidsStored()).singleElement().extracting(Participation::operadorEconomicoId)
        .isNull();
    assertThat(catalogued.byName()).isEmpty();
  }

  // Two rows the source published as different consortia but named alike once folded become one
  // entry holding both member lists. Named because it is a real cost of keying on the folded name,
  // not because it has been observed: the alternative mints a second entry on every restatement
  // that repunctuates a name, which is the commoner failure by far.
  @Test
  void two_consortia_of_one_procedure_whose_names_fold_alike_become_one_entry() {
    theStoreCataloguesOnDemand();
    nothingIsAlreadyMinted();

    ConsortiumOperadores catalogued =
        store(
            List.of(
                consortium(null, UTE, null, member(PRACE, PRACE_ID)),
                consortium(null, "UTE Prace-Taboada, Ramos", null, member(TABOADA, TABOADA_ID))),
            List.of());

    assertThat(catalogued.byName()).hasSize(1);
    OperadorId ute = catalogued.at(matchable(UTE)).operadorId();
    assertThat(theMembershipsStored())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new UteMembership(ute, cataloguedUnder(PRACE_ID).id(), LICITACION_ID),
            new UteMembership(ute, cataloguedUnder(TABOADA_ID).id(), LICITACION_ID));
  }

  // A bid is not a contract, so it neither promotes a name nor enters the retained set — and for
  // an unidentified consortium the name its bid published is the only name it will ever have.
  @Test
  void cataloguing_the_consortium_promotes_no_name_and_retains_none() {
    theStoreCataloguesOnDemand();
    catalogue.put(
        UTE_ID,
        new OperadorEconomico(
            new OperadorId(UUID.randomUUID()),
            UTE_ID,
            "UTE PRACE TABOADA",
            true,
            new NomeRank(LocalDate.of(2024, 1, 1), 7L),
            Set.of()));

    store(List.of(consortium(null, UTE, UTE_ID, member(PRACE, PRACE_ID))), List.of());

    verify(operadores, never()).promoteName(any(), any(), any());
    verify(operadores, never()).retainName(any());
  }

  private ConsortiumOperadores store(
      List<PublishedBidder> bidders, List<PublishedFormalisation> formalisations) {
    return store(List.of(), bidders, formalisations);
  }

  private ConsortiumOperadores store(
      List<Lote> lotes,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations) {
    return stored(lotes, bidders, formalisations).operadores();
  }

  private StoredConsortia stored(
      List<Lote> lotes,
      List<PublishedBidder> bidders,
      List<PublishedFormalisation> formalisations) {
    return new StoreLicitacionConsortia(
            new ResolveOperador(operadores), participations, memberships)
        .store(LICITACION_ID, lotes, bidders, formalisations);
  }

  private OperadorEconomico cataloguedUnder(FiscalIdentifier fiscalId) {
    return catalogue.get(fiscalId);
  }

  /** Every operador the store was asked to catalogue, in the order it was asked. */
  private List<OperadorEconomico> theOperadoresCatalogued() {
    ArgumentCaptor<OperadorEconomico> catalogued = ArgumentCaptor.forClass(OperadorEconomico.class);
    verify(operadores, atLeastOnce()).insert(catalogued.capture());
    return catalogued.getAllValues();
  }

  private List<UteMembership> theMembershipsStored() {
    ArgumentCaptor<UteMembership> stored = ArgumentCaptor.forClass(UteMembership.class);
    verify(memberships, atLeastOnce()).upsert(stored.capture());
    return stored.getAllValues();
  }

  private List<Participation> theBidsStored() {
    ArgumentCaptor<Participation> stored = ArgumentCaptor.forClass(Participation.class);
    verify(participations, atLeastOnce()).upsert(stored.capture());
    return stored.getAllValues();
  }

  private static MatchableName matchable(String publishedName) {
    return MatchableName.of(publishedName).orElseThrow();
  }

  private static PublishedConsortiumMember member(
      String name, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedConsortiumMember(name, fiscalIdentifier);
  }

  private static PublishedBidder consortium(
      @Nullable String loteKey,
      @Nullable String name,
      @Nullable FiscalIdentifier fiscalIdentifier,
      PublishedConsortiumMember... members) {
    return new PublishedBidder.Consortium(loteKey, name, fiscalIdentifier, List.of(members));
  }

  private static PublishedFormalisation formalisation(
      @Nullable String loteKey, String contratista, @Nullable FiscalIdentifier fiscalIdentifier) {
    return new PublishedFormalisation(
        loteKey,
        LocalDate.of(2024, 7, 10),
        contratista,
        fiscalIdentifier,
        "España",
        new Money(new BigDecimal("206996.66")));
  }

  /** A lote as the procedure's own store answered it, carrying the identity a bid hangs off. */
  private static Lote storedLote(String identifier) {
    return new Lote(new LoteId(UUID.randomUUID()), LICITACION_ID, identifier, null, null, false);
  }

  /** A procedure no earlier import has minted a consortium for. */
  private void nothingIsAlreadyMinted() {
    when(participations.findConsortiumOperador(any(), any())).thenReturn(Optional.empty());
  }

  /**
   * A catalogue a bid can add to, shared across the whole call as it would be inside the use case's
   * transaction, so a second member naming the same firm reads what the first one wrote. An
   * identifier-less consortium is answered like any other insert and held in no map: nothing in the
   * catalogue could find it again, which is the point of it.
   */
  private void theStoreCataloguesOnDemand() {
    theStoreAcceptsEveryBid();
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
                      incoming.ute(),
                      incoming.nameRank(),
                      Set.of());
              if (stored.fiscalId() != null) {
                catalogue.put(stored.fiscalId(), stored);
              }
              return stored;
            });
  }

  /** The participation store, assigning the identity the database would on insert. */
  private void theStoreAcceptsEveryBid() {
    when(participations.upsert(any()))
        .thenAnswer(
            invocation -> {
              Participation incoming = invocation.getArgument(0);
              return new Participation(
                  new ParticipationId(UUID.randomUUID()),
                  incoming.licitacionId(),
                  incoming.loteId(),
                  incoming.operadorEconomicoId(),
                  incoming.won(),
                  incoming.withdrawn());
            });
  }
}
