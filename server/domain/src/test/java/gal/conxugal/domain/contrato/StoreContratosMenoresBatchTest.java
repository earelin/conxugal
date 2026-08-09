package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeAlternativo;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.operador.OperadorRepository;
import gal.conxugal.domain.organo.OrganoId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * What this class does is drive two ports, so a few of these assert on the calls the operador
 * store received. Promoting a name and retaining one are void, and the order of the two is itself
 * the rule the store's own contract states — neither leaves a return value to assert on.
 */
@ExtendWith(MockitoExtension.class)
class StoreContratosMenoresBatchTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final OperadorId INCUMBENT_ID = new OperadorId(UUID.randomUUID());
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);

  @Mock
  private OperadorRepository operadores;

  @Mock
  private ContratoMenorRepository contratos;

  private final List<ContratoMenor> upserted = new ArrayList<>();
  private final List<OperadorEconomico> created = new ArrayList<>();
  private final Map<FiscalIdentifier, OperadorEconomico> catalogue = new LinkedHashMap<>();

  @Test
  void award_whose_fiscal_identifier_is_absent_is_stored_under_no_operador() {
    theStoreAcceptsTheBatch();

    store(award(1L, JUNE, "ACME SL", null));

    assertThat(upserted)
        .singleElement()
        .extracting(ContratoMenor::operadorEconomico)
        .isNull();
    verifyNoInteractions(operadores);
  }

  // Whitespace-only is the same branch as absent, and it is the branch that would otherwise
  // catalogue an operador nobody named — the shared "unknown" row that pools unrelated awards.
  @Test
  void award_whose_fiscal_identifier_is_only_whitespace_is_stored_under_no_operador() {
    theStoreAcceptsTheBatch();

    store(award(1L, JUNE, "ACME SL", "   "));

    assertThat(upserted)
        .singleElement()
        .extracting(ContratoMenor::operadorEconomico)
        .isNull();
    verifyNoInteractions(operadores);
  }

  // Only emptiness disqualifies an identifier: a foreign VAT number or a malformed NIF is a real
  // award, and refusing it would discard one.
  @Test
  void award_with_an_irregular_but_non_empty_identifier_is_attached_to_an_operador() {
    theStoreCataloguesOnDemand();

    store(award(1L, JUNE, "Fournitures SARL", "FR-4711/B"));

    assertThat(created)
        .singleElement()
        .extracting(OperadorEconomico::fiscalId)
        .isEqualTo(new FiscalIdentifier("FR-4711/B"));
    assertThat(upserted)
        .singleElement()
        .extracting(ContratoMenor::operadorEconomico)
        .isEqualTo(catalogue.get(new FiscalIdentifier("FR-4711/B")));
  }

  // The lower-case spelling arrives first, so the canonical form cannot be coming from whichever
  // contract happened to be read first. The second contract finds what the first created.
  @Test
  void identifiers_differing_only_in_padding_or_case_yield_one_operador() {
    theStoreCataloguesOnDemand();

    store(award(1L, MAY, "ACME SL", "b12345678"), award(2L, JUNE, "ACME SL", " B12345678 "));

    assertThat(created)
        .singleElement()
        .extracting(OperadorEconomico::fiscalId)
        .isEqualTo(new FiscalIdentifier("B12345678"));
    assertThat(upserted)
        .extracting(ContratoMenor::operadorEconomico)
        .containsExactly(
            catalogue.get(new FiscalIdentifier("B12345678")),
            catalogue.get(new FiscalIdentifier("B12345678")));
  }

  // Over-merging two suppliers into one is as damaging as splitting one in two, and it fails just
  // as silently, so the other side of the matching rule is asserted on its own.
  @Test
  void identifiers_differing_by_spacing_punctuation_or_one_character_yield_one_operador_each() {
    theStoreCataloguesOnDemand();

    store(
        award(1L, JUNE, "ACME SL", "B12345678"),
        award(2L, JUNE, "ACME SL", "B 12345678"),
        award(3L, JUNE, "ACME SL", "B-12345678"),
        award(4L, JUNE, "ACME SL", "B12345679"));

    assertThat(created)
        .extracting(OperadorEconomico::fiscalId)
        .containsExactly(
            new FiscalIdentifier("B12345678"),
            new FiscalIdentifier("B 12345678"),
            new FiscalIdentifier("B-12345678"),
            new FiscalIdentifier("B12345679"));
  }

  @Test
  void operador_no_contract_named_before_is_catalogued_under_the_name_and_rank_that_named_it() {
    theStoreCataloguesOnDemand();

    store(award(4711L, JUNE, "ACME SL", "B12345678"));

    assertThat(created)
        .singleElement()
        .satisfies(
            operador -> {
              assertThat(operador.name()).isEqualTo("ACME SL");
              assertThat(operador.nameRank()).isEqualTo(new NomeRank(JUNE, 4711L));
            });
  }

  @Test
  void contract_that_outranks_the_incumbent_moves_its_name_in_and_the_displaced_one_out() {
    catalogued("B12345678", "Antiga Denominación SL", MAY, 1L);

    store(award(2L, JUNE, "ACME SL", "B12345678"));

    ArgumentCaptor<NomeAlternativo> retained = ArgumentCaptor.forClass(NomeAlternativo.class);
    InOrder promotionThenRetention = inOrder(operadores);
    promotionThenRetention
        .verify(operadores)
        .promoteName(INCUMBENT_ID, "ACME SL", new NomeRank(JUNE, 2L));
    promotionThenRetention.verify(operadores).retainName(retained.capture());
    assertThat(retained.getValue())
        .usingRecursiveComparison()
        .isEqualTo(
            new NomeAlternativo(INCUMBENT_ID, "Antiga Denominación SL", new NomeRank(MAY, 1L)));
  }

  @Test
  void contract_that_does_not_outrank_the_incumbent_retains_its_own_name_and_moves_nothing() {
    catalogued("B12345678", "ACME SL", JUNE, 2L);

    store(award(1L, MAY, "Antiga Denominación SL", "B12345678"));

    assertThat(theNameRetained())
        .usingRecursiveComparison()
        .isEqualTo(
            new NomeAlternativo(INCUMBENT_ID, "Antiga Denominación SL", new NomeRank(MAY, 1L)));
    verify(operadores, never()).promoteName(any(), any(), any());
  }

  // There is no second name to file, and filing this one would leave the operador retaining the
  // name it displays — the state its own aggregate refuses to be built in.
  @Test
  void contract_republishing_the_displayed_name_advances_the_rank_and_retains_nothing() {
    catalogued("B12345678", "ACME SL", MAY, 1L);

    store(award(2L, JUNE, "ACME SL", "B12345678"));

    verify(operadores).promoteName(INCUMBENT_ID, "ACME SL", new NomeRank(JUNE, 2L));
    verify(operadores, never()).retainName(any());
  }

  // Replaying a batch after a crash: the rank comparison is a strict win, so nothing is promoted
  // and no retained name is re-dated.
  @Test
  void contract_already_held_moves_nothing_and_retains_nothing() {
    catalogued("B12345678", "ACME SL", JUNE, 4711L);

    store(award(4711L, JUNE, "ACME SL", "B12345678"));

    verify(operadores, never()).promoteName(any(), any(), any());
    verify(operadores, never()).retainName(any());
  }

  // Undated ranks last however high its source identifier and however late it arrives, so the
  // displayed name cannot be taken by a contract nobody can date.
  @Test
  void undated_contract_never_displaces_one_that_carries_its_date() {
    catalogued("B12345678", "ACME SL", MAY, 1L);

    store(award(9999L, null, "Sen Data SL", "B12345678"));

    assertThat(theNameRetained())
        .usingRecursiveComparison()
        .isEqualTo(new NomeAlternativo(INCUMBENT_ID, "Sen Data SL", new NomeRank(null, 9999L)));
    verify(operadores, never()).promoteName(any(), any(), any());
  }

  // Which keeps the choice total: an operador all of whose contracts are undated still has exactly
  // one name, settled between them by the higher source identifier.
  @Test
  void undated_contracts_settle_the_displayed_name_on_the_higher_source_identifier() {
    catalogued("B12345678", "Primeira SL", null, 5L);

    store(award(9L, null, "Segunda SL", "B12345678"));

    verify(operadores).promoteName(INCUMBENT_ID, "Segunda SL", new NomeRank(null, 9L));
  }

  // Identity is the identifier alone, so an award the source published without a name is still
  // catalogued under it. Nothing invents a name for the operador in the meantime.
  @Test
  void award_that_published_no_name_catalogues_the_operador_under_the_empty_name() {
    theStoreCataloguesOnDemand();

    store(award(1L, JUNE, null, "B12345678"));

    assertThat(created)
        .singleElement()
        .extracting(OperadorEconomico::name)
        .isEqualTo("");
  }

  @Test
  void answers_with_the_counts_the_store_reported() {
    when(contratos.upsertAll(anyCollection())).thenReturn(new UpsertCounts(3, 2));

    UpsertCounts counts = store(award(1L, JUNE, "ACME SL", null));

    assertThat(counts).isEqualTo(new UpsertCounts(3, 2));
  }

  @Test
  void stores_every_published_value_of_the_contract_under_the_awarding_organo() {
    theStoreAcceptsTheBatch();

    store(award(4711L, JUNE, "ACME SL", null));

    assertThat(upserted)
        .singleElement()
        .usingRecursiveComparison()
        .isEqualTo(
            new ContratoMenor(
                4711L,
                ORGANO_ID,
                JUNE,
                "Obxecto 4711",
                new Money(new BigDecimal("100.00")),
                "1 mes",
                null));
  }

  /**
   * The one name the store was asked to retain. Captured rather than matched, because a retained
   * name is distinct by its spelling alone — an expected value would match whatever rank the call
   * carried, which is the half these tests are about.
   */
  private NomeAlternativo theNameRetained() {
    ArgumentCaptor<NomeAlternativo> retained = ArgumentCaptor.forClass(NomeAlternativo.class);
    verify(operadores).retainName(retained.capture());
    return retained.getValue();
  }

  private UpsertCounts store(ContratoMenorSourceEntry... entries) {
    return new StoreContratosMenoresBatch(operadores, contratos)
        .store(List.of(entries), ORGANO_ID);
  }

  private static ContratoMenorSourceEntry award(
      long sourceId,
      @Nullable LocalDate publicationDate,
      @Nullable String awardeeName,
      @Nullable String awardeeFiscalId) {
    return new ContratoMenorSourceEntry(
        sourceId,
        publicationDate,
        "Obxecto %d".formatted(sourceId),
        new Money(new BigDecimal("100.00")),
        "1 mes",
        awardeeName,
        awardeeFiscalId);
  }

  /**
   * The catalogue as the store maintains it: an operador is found once something has created it,
   * and creating one assigns the identity the database would. It is the same transaction, so a
   * contract reads what the contract before it in the batch wrote.
   */
  private void theStoreCataloguesOnDemand() {
    theStoreAcceptsTheBatch();
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
                      incoming.nameRank(),
                      Set.of());
              created.add(stored);
              catalogue.put(stored.fiscalId(), stored);
              return stored;
            });
  }

  /** An operador an earlier import already catalogued, displayed under {@code name}. */
  private void catalogued(
      String fiscalId, String name, @Nullable LocalDate date, long sourceId) {
    OperadorEconomico operador =
        new OperadorEconomico(
            INCUMBENT_ID,
            new FiscalIdentifier(fiscalId),
            name,
            new NomeRank(date, sourceId),
            Set.of());
    when(operadores.findByFiscalId(operador.fiscalId())).thenReturn(Optional.of(operador));
  }

  private void theStoreAcceptsTheBatch() {
    when(contratos.upsertAll(anyCollection()))
        .thenAnswer(
            invocation -> {
              Collection<ContratoMenor> batch = invocation.getArgument(0);
              upserted.addAll(batch);
              return new UpsertCounts(batch.size(), 0);
            });
  }
}
