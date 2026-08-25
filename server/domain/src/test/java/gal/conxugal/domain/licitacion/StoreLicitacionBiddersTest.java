package gal.conxugal.domain.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.operador.ResolveOperador;
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
 * The bidder half of a procedure's competition: which rows become a bid, which party each names,
 * and which rows this path never sees at all.
 *
 * <p>The real {@link ResolveOperador} is wired rather than stubbed, because what these assert on is
 * the routing into it and what comes back out — a stub of the collaborator would let the routing
 * agree with itself.
 */
@ExtendWith(MockitoExtension.class)
class StoreLicitacionBiddersTest {

  private static final LicitacionId LICITACION_ID = new LicitacionId(UUID.randomUUID());
  private static final FiscalIdentifier EQUINSE_ID = new FiscalIdentifier("A41111220");
  private static final FiscalIdentifier AQUAGEST_ID = new FiscalIdentifier("B15112222");
  private static final String EQUINSE = "EQUINSE, S.A.";
  private static final String AQUAGEST = "AQUAGEST, S.A.";

  @Mock
  private OperadorRepository operadores;

  @Mock
  private ParticipationRepository participations;

  private final Map<FiscalIdentifier, OperadorEconomico> catalogue = new HashMap<>();

  // R18's rule: the bid names its party by reference and carries no name of its own, so what a
  // reader is shown comes from the operador rather than from a copy on this row.
  @Test
  void single_firm_bidder_stores_naming_the_operador_its_identifier_resolves_to() {
    theStoreCataloguesOnDemand();

    List<Participation> stored = store(List.of(), singleFirm(null, EQUINSE, EQUINSE_ID));

    assertThat(stored)
        .singleElement()
        .satisfies(
            bid -> {
              assertThat(bid.licitacionId()).isEqualTo(LICITACION_ID);
              assertThat(bid.loteId()).isNull();
              assertThat(bid.operadorEconomicoId()).isEqualTo(catalogued(EQUINSE_ID).id());
              assertThat(bid.won()).isFalse();
              assertThat(bid.withdrawn()).isFalse();
            });
  }

  @Test
  void bid_is_keyed_to_the_lote_its_row_named() {
    theStoreCataloguesOnDemand();
    Lote lote = storedLote("05");

    List<Participation> stored = store(List.of(lote), singleFirm("5", EQUINSE, EQUINSE_ID));

    assertThat(stored).singleElement().extracting(Participation::loteId).isEqualTo(lote.id());
  }

  // R16's exception: the party is recorded as the participation of no catalogue entry rather than
  // dropped, so the bid the source published survives and nothing invented stands in for it.
  @Test
  void bidder_whose_identifier_is_unusable_stores_naming_nobody() {
    theStoreAcceptsEveryBid();

    List<Participation> stored = store(List.of(), singleFirm(null, "Sen NIF SL", null));

    assertThat(stored)
        .singleElement()
        .extracting(Participation::operadorEconomicoId)
        .isNull();
  }

  @Test
  void bidder_whose_identifier_is_unusable_costs_the_other_bidders_on_the_procedure_nothing() {
    theStoreCataloguesOnDemand();

    List<Participation> stored =
        store(
            List.of(),
            singleFirm(null, EQUINSE, EQUINSE_ID),
            singleFirm(null, "Sen NIF SL", null),
            singleFirm(null, AQUAGEST, AQUAGEST_ID));

    assertThat(stored)
        .extracting(Participation::operadorEconomicoId)
        .containsExactly(catalogued(EQUINSE_ID).id(), null, catalogued(AQUAGEST_ID).id());
  }

  // A consortium's identifier may come from the formalisation rather than the bidder row, so this
  // path never decides whether it is identified — and never offers its NIF cell to the catalogue.
  @Test
  void consortium_row_reaches_neither_the_catalogue_nor_the_store() {
    store(List.of(), consortium(null, "UTE PRACE-TABOADA RAMOS"));

    verifyNoInteractions(operadores);
    verifyNoInteractions(participations);
  }

  @Test
  void consortium_row_beside_single_firms_leaves_the_firms_stored_and_itself_unstored() {
    theStoreCataloguesOnDemand();

    List<Participation> stored =
        store(
            List.of(),
            singleFirm(null, EQUINSE, EQUINSE_ID),
            consortium(null, "UTE PRACE-TABOADA RAMOS"),
            singleFirm(null, AQUAGEST, AQUAGEST_ID));

    assertThat(stored)
        .extracting(Participation::operadorEconomicoId)
        .containsExactly(catalogued(EQUINSE_ID).id(), catalogued(AQUAGEST_ID).id());
  }

  // Two bids by one firm at two award points are two bids, so the lote is what tells them apart.
  @Test
  void firm_bidding_at_two_award_points_stores_one_bid_at_each() {
    theStoreCataloguesOnDemand();
    Lote first = storedLote("1");
    Lote second = storedLote("2");

    List<Participation> stored =
        store(
            List.of(first, second),
            singleFirm("1", EQUINSE, EQUINSE_ID),
            singleFirm("2", EQUINSE, EQUINSE_ID));

    assertThat(stored)
        .extracting(Participation::loteId)
        .containsExactly(first.id(), second.id());
    assertThat(stored)
        .extracting(Participation::operadorEconomicoId)
        .containsOnly(catalogued(EQUINSE_ID).id());
  }

  // Filing it against the procedure would collide with every other such bid on the natural key,
  // and skipping it would lose a published bidder silently, so the procedure fails instead.
  @Test
  void bidder_naming_lote_the_procedure_has_not_stored_refuses_the_procedure() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> store(List.of(storedLote("1")), singleFirm("7", EQUINSE, EQUINSE_ID)))
        .withMessageContaining("7");
  }

  // A bid catalogues what nothing named before, and that much is forced: R16 needs the bid to be
  // recorded and R3 needs its identifier to resolve to something. What it does not do is rank —
  // the entry is created behind every contract, so the first one to name it takes the display.
  @Test
  void bid_naming_firm_no_contract_named_catalogues_it_under_the_published_name() {
    theStoreCataloguesOnDemand();

    store(List.of(), singleFirm(null, EQUINSE, EQUINSE_ID));

    assertThat(catalogued(EQUINSE_ID))
        .satisfies(
            operador -> {
              assertThat(operador.name()).isEqualTo(EQUINSE);
              assertThat(operador.nameRank()).isEqualTo(NomeRank.unranked());
            });
  }

  private List<Participation> store(List<Lote> lotes, PublishedBidder... bidders) {
    return new StoreLicitacionBidders(new ResolveOperador(operadores), participations)
        .store(LICITACION_ID, lotes, List.of(bidders));
  }

  private OperadorEconomico catalogued(FiscalIdentifier fiscalId) {
    return catalogue.get(fiscalId);
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

  /** A lote as the procedure's own store answered it, carrying the identity a bid hangs off. */
  private static Lote storedLote(String identifier) {
    return new Lote(
        new LoteId(UUID.randomUUID()), LICITACION_ID, identifier, null, null, false);
  }

  /**
   * A catalogue a bid can add to, shared across the whole call as it would be inside the use case's
   * transaction, so a second bidder naming the same firm reads what the first one wrote.
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
                      false,
                      incoming.nameRank(),
                      Set.of());
              catalogue.put(stored.fiscalId(), stored);
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
