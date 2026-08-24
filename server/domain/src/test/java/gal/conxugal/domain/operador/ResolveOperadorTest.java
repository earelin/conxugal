package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The derivation every contract family shares, driven with the loose values it takes rather than
 * with any one family's source row.
 *
 * <p>What this class does is drive one port, so several of these assert on the calls the operador
 * store received. Promoting a name and retaining one are void, and the order of the two is itself
 * the rule the store's own contract states — neither leaves a return value to assert on.
 */
@ExtendWith(MockitoExtension.class)
class ResolveOperadorTest {

  private static final FiscalIdentifier ACME_ID = new FiscalIdentifier("B12345678");
  private static final OperadorId INCUMBENT_ID = new OperadorId(UUID.randomUUID());
  private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);
  private static final LocalDate MAY = LocalDate.of(2026, 5, 1);
  private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);

  @Mock
  private OperadorRepository operadores;

  @Test
  void publication_naming_an_operador_nothing_named_before_catalogues_it_under_that_rank() {
    nothingIsCatalogued();

    Optional<OperadorEconomico> resolved = resolve("b12345678", "ACME SL", JUNE, 4711L);

    assertThat(resolved)
        .get()
        .satisfies(
            operador -> {
              assertThat(operador.fiscalId()).isEqualTo(ACME_ID);
              assertThat(operador.name()).isEqualTo("ACME SL");
              assertThat(operador.nameRank()).isEqualTo(new NomeRank(JUNE, 4711L));
            });
  }

  // Identity is the canonical identifier alone, so a padded, differently-cased publication finds
  // what an earlier one catalogued rather than cataloguing a second entry beside it.
  @Test
  void publication_naming_catalogued_operador_answers_with_it_and_catalogues_nothing() {
    catalogued("ACME SL", JUNE, 2L);

    Optional<OperadorEconomico> resolved = resolve(" b12345678 ", "ACME SL", JUNE, 2L);

    assertThat(resolved)
        .get()
        .extracting(OperadorEconomico::id)
        .isEqualTo(INCUMBENT_ID);
    verify(operadores, never()).insert(any());
  }

  // Never a placeholder, and never a shared "unknown" row that would pool unrelated parties under
  // one identity — the caller is left with nothing to attach rather than something invented.
  @Test
  void publication_whose_identifier_is_unusable_yields_no_operador() {
    Optional<OperadorEconomico> resolved = resolve("   ", "ACME SL", JUNE, 1L);

    assertThat(resolved).isEmpty();
    verifyNoInteractions(operadores);
  }

  // Retaining the displaced name before the promotion would ask the store to file a name that is
  // still the displayed one, which it declines — losing the name silently.
  @Test
  void outranking_name_is_promoted_before_the_name_it_displaced_is_retained() {
    catalogued("Antiga Denominación SL", MAY, 1L);

    resolve("B12345678", "ACME SL", JUNE, 2L);

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
  void name_that_does_not_outrank_the_incumbent_is_retained_and_moves_nothing() {
    catalogued("ACME SL", JUNE, 2L);

    resolve("B12345678", "Antiga Denominación SL", MAY, 1L);

    assertThat(theNameRetained())
        .usingRecursiveComparison()
        .isEqualTo(
            new NomeAlternativo(INCUMBENT_ID, "Antiga Denominación SL", new NomeRank(MAY, 1L)));
    verify(operadores, never()).promoteName(any(), any(), any());
  }

  // Replaying an import: the rank comparison is a strict win, so nothing is promoted, no retained
  // name is re-dated, and the displayed name cannot flap.
  @Test
  void resolving_the_same_publication_again_moves_nothing_and_retains_nothing() {
    catalogued("ACME SL", JUNE, 4711L);

    resolve("B12345678", "ACME SL", JUNE, 4711L);

    verify(operadores, never()).promoteName(any(), any(), any());
    verify(operadores, never()).retainName(any());
  }

  // Identity is the identifier alone, so a publication the source carried no name for is still
  // catalogued under it. Nothing invents a name for the operador in the meantime.
  @Test
  void publication_that_carried_no_name_catalogues_the_operador_under_the_empty_name() {
    nothingIsCatalogued();

    Optional<OperadorEconomico> resolved = resolve("B12345678", null, JUNE, 1L);

    assertThat(resolved).get().extracting(OperadorEconomico::name).isEqualTo("");
  }

  // The empty name is what an operador nothing has named is displayed as, never a name that can
  // win R4 — and it is not a name the operador has borne, so it does not enter the retained set
  // either. A blank the source left would otherwise take the display from a name really published.
  @Test
  void publication_that_carried_no_name_neither_promotes_nor_retains() {
    catalogued("ACME SL", MAY, 1L);

    resolve("B12345678", null, JUNE, 2L);

    verify(operadores, never()).promoteName(any(), any(), any());
    verify(operadores, never()).retainName(any());
  }

  // Undated ranks last however high its source identifier and however late it arrives, so the
  // displayed name cannot be taken by a publication nobody can date.
  @Test
  void undated_publication_never_displaces_one_that_carries_its_date() {
    catalogued("ACME SL", MAY, 1L);

    resolve("B12345678", "Sen Data SL", null, 9999L);

    assertThat(theNameRetained())
        .usingRecursiveComparison()
        .isEqualTo(new NomeAlternativo(INCUMBENT_ID, "Sen Data SL", new NomeRank(null, 9999L)));
    verify(operadores, never()).promoteName(any(), any(), any());
  }

  // Which keeps the choice total: an operador every publication of which is undated still has
  // exactly one name, settled between them by the higher source identifier.
  @Test
  void undated_publications_settle_the_displayed_name_on_the_higher_source_identifier() {
    catalogued("Primeira SL", null, 5L);

    resolve("B12345678", "Segunda SL", null, 9L);

    verify(operadores).promoteName(INCUMBENT_ID, "Segunda SL", new NomeRank(null, 9L));
  }

  // Every name the operador has borne survives, so three publications under three names leave the
  // two it no longer displays retained beside it, each under the rank it was published at. The
  // second read answers what the first promotion actually wrote, as it would inside the caller's
  // transaction — stubbing the promoted state instead would assert a conclusion the stub supplied,
  // and would stay green with the promotion deleted.
  @Test
  void operador_published_under_three_names_retains_the_two_it_no_longer_displays() {
    theStoreDisplays("Primeira SL", MARCH, 1L);

    resolve("B12345678", "Segunda SL", MAY, 2L);
    resolve("B12345678", "Terceira SL", JUNE, 3L);

    ArgumentCaptor<NomeAlternativo> retained = ArgumentCaptor.forClass(NomeAlternativo.class);
    verify(operadores, times(2)).retainName(retained.capture());
    assertThat(retained.getAllValues())
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(
            new NomeAlternativo(INCUMBENT_ID, "Primeira SL", new NomeRank(MARCH, 1L)),
            new NomeAlternativo(INCUMBENT_ID, "Segunda SL", new NomeRank(MAY, 2L)));
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

  private Optional<OperadorEconomico> resolve(
      @Nullable String publishedFiscalId,
      @Nullable String publishedName,
      @Nullable LocalDate date,
      long sourceId) {
    return new ResolveOperador(operadores)
        .resolve(publishedFiscalId, publishedName, new NomeRank(date, sourceId));
  }

  /** A catalogue nothing has named yet, which assigns the identity the database would on insert. */
  private void nothingIsCatalogued() {
    when(operadores.findByFiscalId(any())).thenReturn(Optional.empty());
    when(operadores.insert(any()))
        .thenAnswer(
            invocation -> {
              OperadorEconomico incoming = invocation.getArgument(0);
              return new OperadorEconomico(
                  new OperadorId(UUID.randomUUID()),
                  incoming.fiscalId(),
                  incoming.name(),
                  false,
                  incoming.nameRank(),
                  Set.of());
            });
  }

  /** An operador an earlier import already catalogued, displayed under {@code name}. */
  private void catalogued(String name, @Nullable LocalDate date, long sourceId) {
    when(operadores.findByFiscalId(ACME_ID))
        .thenReturn(Optional.of(incumbent(name, date, sourceId)));
  }

  /**
   * The same, for a sequence: what the store displays moves when a promotion writes it, so a second
   * resolution reads the name and rank the first one left rather than a state the stub supplied.
   * Inside the caller's transaction that is what the store would answer.
   */
  private void theStoreDisplays(String name, @Nullable LocalDate date, long sourceId) {
    AtomicReference<OperadorEconomico> displayed =
        new AtomicReference<>(incumbent(name, date, sourceId));
    when(operadores.findByFiscalId(ACME_ID))
        .thenAnswer(invocation -> Optional.of(displayed.get()));
    doAnswer(
            invocation -> {
              String promotedName = invocation.getArgument(1);
              NomeRank promotedRank = invocation.getArgument(2);
              displayed.set(incumbent(promotedName, promotedRank));
              return null;
            })
        .when(operadores)
        .promoteName(any(), any(), any());
  }

  private static OperadorEconomico incumbent(
      String name, @Nullable LocalDate date, long sourceId) {
    return incumbent(name, new NomeRank(date, sourceId));
  }

  private static OperadorEconomico incumbent(String name, NomeRank nameRank) {
    return new OperadorEconomico(INCUMBENT_ID, ACME_ID, name, false, nameRank, Set.of());
  }
}
