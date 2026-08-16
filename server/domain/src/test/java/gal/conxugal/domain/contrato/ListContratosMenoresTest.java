package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The browse read with no database under it, which is what leaves the ordering visible: the store
 * is stubbed, so what the use case hands it is the whole of its observable behaviour.
 *
 * <p><b>The four expected clauses are written out rather than rendered from {@link SortKey}.</b>
 * Rendering them would restate the production expression on both sides of the assertion, and a
 * key that came to name a property no table has a column for would move the expectation along
 * with the emitted clause and stay green. Written out, they are a fixed point — the same one the
 * schema test pins the plan of. That holds of the four ordering assertions specifically; the
 * stubs below that key on a whole pageable do render the ordering from {@code SortKey}, since
 * what they are pinning is which page comes back rather than what the clause says, and the four
 * above already hold the clause itself still.
 *
 * <p><b>The store is stubbed against an exact {@code Pageable} wherever the answer is what is
 * asserted.</b> Under strict stubbing that is itself the argument assertion: a use case handing a
 * different page, year or ordering leaves the stub unused and the test red, so nothing has to be
 * verified afterwards.
 */
@ExtendWith(MockitoExtension.class)
class ListContratosMenoresTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final YearSelection YEAR = YearSelection.of(2025);

  /** A year read fifty at a time, so the count is of neither the page nor its content. */
  private static final long WHOLE_YEAR = 120;

  private static final int SIZE = 50;

  private static final VisibleContratoMenor EARLIEST = visible(1, "2025-01-04", "1200.00");
  private static final VisibleContratoMenor LATEST = visible(2, "2025-11-27", "800.50");
  private static final VisibleContratoMenor SMALLEST = visible(3, "2025-06-13", "42.00");
  private static final VisibleContratoMenor LARGEST = visible(4, "2025-03-02", "14999.99");

  @Mock
  private OrganoRepository organos;

  @Mock
  private VisibleContratoMenorRepository visibleContratos;

  // ------------------------------------------- the ordering the store is handed, all four of them

  @Test
  void hands_the_store_the_date_column_ascending_then_the_source_identifier() {
    assertOrdering(
        SortKey.PUBLICATION_DATE,
        Sort.Order.Direction.ASC,
        tuple("publication_date", Sort.Order.Direction.ASC),
        tuple("source_id", Sort.Order.Direction.ASC));
  }

  @Test
  void hands_the_store_the_date_column_descending_then_the_source_identifier() {
    assertOrdering(
        SortKey.PUBLICATION_DATE,
        Sort.Order.Direction.DESC,
        tuple("publication_date", Sort.Order.Direction.DESC),
        tuple("source_id", Sort.Order.Direction.DESC));
  }

  @Test
  void hands_the_store_the_amount_column_ascending_then_the_source_identifier() {
    assertOrdering(
        SortKey.AMOUNT,
        Sort.Order.Direction.ASC,
        tuple("amount", Sort.Order.Direction.ASC),
        tuple("source_id", Sort.Order.Direction.ASC));
  }

  @Test
  void hands_the_store_the_amount_column_descending_then_the_source_identifier() {
    assertOrdering(
        SortKey.AMOUNT,
        Sort.Order.Direction.DESC,
        tuple("amount", Sort.Order.Direction.DESC),
        tuple("source_id", Sort.Order.Direction.DESC));
  }

  /**
   * An ordering that reached the use case on the pageable is replaced, not added to. It is the one
   * way a property this system never named could still reach a native statement, and the pageable
   * is the only parameter wide enough to carry one.
   */
  @Test
  void discards_any_ordering_that_arrived_on_the_pageable_rather_than_appending_to_it() {
    Pageable handed =
        orderingHandedFor(
            SortKey.AMOUNT,
            Sort.Order.Direction.DESC,
            Pageable.from(0, SIZE, Sort.of(Sort.Order.asc("obxecto; DROP TABLE contrato_menor"))));

    assertOrders(
        handed,
        tuple("amount", Sort.Order.Direction.DESC),
        tuple("source_id", Sort.Order.Direction.DESC));
  }

  // --------------------------------------------------- and that the four are four different reads

  /**
   * The four are asserted together rather than one test apart, because what has to hold is that
   * they are <b>distinguishable</b>: each answers the page its own ordering selected. Four
   * assertions in four tests would all still pass if one ordering quietly served another's page.
   *
   * <p>This is as far as a stubbed store can carry <em>the largest contract of the whole year is
   * on the first page sorted by amount descending</em>: that the pair asks for page one in that
   * ordering and answers what the store gave back. Whether the store's answer really is the
   * largest of the year rather than of some subset is a property of the statement, and it is
   * proven where the statement runs — {@code JdbcVisibleContratoMenorRepositoryIntegrationTest}.
   */
  @Test
  void answers_each_of_the_four_orderings_with_the_page_that_ordering_selected() {
    organoExists();
    firstRowIs(SortKey.PUBLICATION_DATE, Sort.Order.Direction.ASC, EARLIEST);
    firstRowIs(SortKey.PUBLICATION_DATE, Sort.Order.Direction.DESC, LATEST);
    firstRowIs(SortKey.AMOUNT, Sort.Order.Direction.ASC, SMALLEST);
    firstRowIs(SortKey.AMOUNT, Sort.Order.Direction.DESC, LARGEST);

    assertThat(
            List.of(
                firstRowOf(SortKey.PUBLICATION_DATE, Sort.Order.Direction.ASC),
                firstRowOf(SortKey.PUBLICATION_DATE, Sort.Order.Direction.DESC),
                firstRowOf(SortKey.AMOUNT, Sort.Order.Direction.ASC),
                firstRowOf(SortKey.AMOUNT, Sort.Order.Direction.DESC)))
        .containsExactly(EARLIEST, LATEST, SMALLEST, LARGEST);
  }

  // ------------------------------------------------------------- the page, the size and the count

  @Test
  void asks_for_the_page_and_the_size_it_was_given_rather_than_defaulting_or_clamping_them() {
    Pageable handed =
        orderingHandedFor(SortKey.AMOUNT, Sort.Order.Direction.DESC, Pageable.from(7, 1));

    assertThat(handed)
        .extracting(Pageable::getNumber, Pageable::getSize)
        .containsExactly(7, 1);
  }

  /**
   * The count is of the selection, so it is the same on every page of it, and the use case neither
   * recomputes it nor rewrites it. Three pages are walked rather than one because each is stubbed
   * against its own exact pageable: under strict stubbing that is what pins the ordering as
   * identical across the three, which is the other half of a page number meaning anything.
   */
  @Test
  void answers_the_count_of_the_whole_selection_on_every_page_of_it() {
    organoExists();
    yearIsThreePagesLong();

    assertThat(List.of(totalOfPage(0), totalOfPage(1), totalOfPage(2)))
        .containsOnly(WHOLE_YEAR);
  }

  // ------------------------------------------------------------------- and the Órgano that is not

  /**
   * The store is asserted untouched as well as the exception raised: <em>refused</em> and
   * <em>answered with an empty page</em> are two different answers, and only the second assertion
   * tells them apart — an implementation that read the year first and threw afterwards would pass
   * on the exception alone.
   */
  @Test
  void refuses_an_organo_that_does_not_exist_rather_than_answering_an_empty_page() {
    when(organos.findById(ORGANO_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> list(SortKey.AMOUNT, Sort.Order.Direction.DESC, Pageable.from(0, SIZE)))
        .isInstanceOf(OrganoNotFoundException.class);
    verifyNoInteractions(visibleContratos);
  }

  // --------------------------------------------------------------------------------------- fixture

  /** The ordering a pair produces on a pageable that asked for none. */
  private void assertOrdering(
      SortKey key, Sort.Order.Direction direction, Tuple... expectedOrders) {
    assertOrders(orderingHandedFor(key, direction, Pageable.from(0, SIZE)), expectedOrders);
  }

  /**
   * Each expected order is a column and a direction, spelled out by the caller. Nothing here
   * derives the tiebreaker's direction from the key's: that they match is what is under test, and
   * a helper that built one from the other could no longer fail when they stopped matching.
   */
  private static void assertOrders(Pageable handed, Tuple... expectedOrders) {
    assertThat(handed.getSort().getOrderBy())
        .extracting(Sort.Order::getProperty, Sort.Order::getDirection)
        .containsExactly(expectedOrders);
  }

  /**
   * The pageable the store was handed, captured as it answers rather than verified afterwards, so
   * an assertion on the ordering reads against the clause itself.
   */
  private Pageable orderingHandedFor(
      SortKey key, Sort.Order.Direction direction, Pageable asked) {
    organoExists();
    AtomicReference<Pageable> handed = new AtomicReference<>();
    when(visibleContratos.page(eq(ORGANO_ID), eq(YEAR), any()))
        .thenAnswer(invocation -> {
          Pageable pageable = invocation.getArgument(2);
          handed.set(pageable);
          return Page.of(List.of(), pageable, WHOLE_YEAR);
        });

    list(key, direction, asked);

    return handed.get();
  }

  private void firstRowIs(
      SortKey key, Sort.Order.Direction direction, VisibleContratoMenor first) {
    pageIs(0, key, direction, first);
  }

  /**
   * The store's answer for exactly one page of one ordering. It is stubbed against the whole
   * pageable rather than loose matchers, so under strict stubbing the page number and the ordering
   * the use case asked for are asserted by the stub matching at all.
   */
  private void pageIs(
      int number, SortKey key, Sort.Order.Direction direction, VisibleContratoMenor row) {
    Pageable expected = Pageable.from(number, SIZE, key.ordering(direction));
    when(visibleContratos.page(ORGANO_ID, YEAR, expected))
        .thenReturn(Page.of(List.of(row), expected, WHOLE_YEAR));
  }

  private VisibleContratoMenor firstRowOf(SortKey key, Sort.Order.Direction direction) {
    return list(key, direction, Pageable.from(0, SIZE))
        .getContent()
        .getFirst();
  }

  private void yearIsThreePagesLong() {
    for (int number = 0; number < 3; number++) {
      pageIs(number, SortKey.AMOUNT, Sort.Order.Direction.DESC, LARGEST);
    }
  }

  private long totalOfPage(int number) {
    return list(SortKey.AMOUNT, Sort.Order.Direction.DESC, Pageable.from(number, SIZE))
        .getTotalSize();
  }

  private Page<VisibleContratoMenor> list(
      SortKey key, Sort.Order.Direction direction, Pageable pageable) {
    return new ListContratosMenores(organos, visibleContratos)
        .list(ORGANO_ID, YEAR, key, direction, pageable);
  }

  private void organoExists() {
    when(organos.findById(ORGANO_ID))
        .thenReturn(
            Optional.of(
                new OrganoDeContratacion(
                    ORGANO_ID, "source-key", "Consellería do Mar", true, true, null)));
  }

  private static VisibleContratoMenor visible(long sourceId, String published, String amount) {
    return new VisibleContratoMenor(
        sourceId,
        LocalDate.parse(published),
        "Subministración",
        new Money(new BigDecimal(amount)),
        "3 meses",
        "Obradoiro do Mar SL",
        new FiscalIdentifier("B12345678"));
  }
}
