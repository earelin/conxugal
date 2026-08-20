package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImportOrganoContratosMenoresTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final String SOURCE_KEY = "242";

  private static final Instant T_ZERO = Instant.parse("2026-08-06T09:00:00Z");
  private static final LocalDate T_ZERO_DAY = LocalDate.of(2026, 8, 6);
  private static final LocalDate FIRST_WINDOW_START = LocalDate.of(2026, 5, 9);
  private static final LocalDate SECOND_WINDOW_START = LocalDate.of(2026, 2, 9);
  private static final LocalDate THIRD_WINDOW_START = LocalDate.of(2025, 11, 12);
  private static final LocalDate HISTORY_FLOOR = LocalDate.of(2018, 1, 1);

  @Mock
  private ContratoMenorSource contratoMenorSource;

  @Mock
  private StoreContratosMenoresBatch batch;

  @Mock
  private ContratoMenorRepository contratos;

  @Mock
  private ContratosMenoresImportStateRepository importStates;

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private Clock clock;

  private final List<Slice> requestedSlices = new ArrayList<>();
  private final AtomicReference<ContratosMenoresImportState> createdState = new AtomicReference<>();
  private final List<ContratoMenorSourceEntry> handedToTheStore = new ArrayList<>();
  private final List<LocalDate> cursorWrites = new ArrayList<>();
  private final AtomicLong stored = new AtomicLong();

  /** One call the source port received, which is what the walk's shape is asserted on. */
  private record Slice(LocalDate from, LocalDate to, int offset) {}

  @Test
  void walks_backwards_in_windows_that_overlap_by_their_boundary_day() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(3, Map.of(FIRST_WINDOW_START, entries(1), SECOND_WINDOW_START, entries(2)));

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .containsExactly(
            new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0),
            new Slice(SECOND_WINDOW_START, FIRST_WINDOW_START, 0));
  }

  // Days, not months, and the dates are pinned rather than recomputed from the production formula.
  // Stepping back by months lands on a window whose own three months end before it began whenever
  // the month it lands in is shorter, and the source refuses that window as over-wide.
  @Test
  void asks_for_windows_no_wider_than_the_shortest_three_calendar_months() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(99, Map.of());

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .allSatisfy(
            slice ->
                assertThat(slice.from())
                    .isAfterOrEqualTo(slice.to().minusMonths(3))
                    .isBefore(slice.to()));
  }

  // The month-end case the pure-month arithmetic got wrong: 2026-05-31 stepped back three months
  // lands on 2026-02-28, whose own three months end on 2026-05-28 — before the window began.
  @Test
  void walks_from_month_end_without_asking_for_an_over_wide_window() {
    resumesFrom(LocalDate.of(2026, 5, 31));
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(99, Map.of());

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .element(0)
        .isEqualTo(new Slice(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 5, 31), 0));
  }

  @Test
  void pages_each_window_to_exhaustion_before_stepping_back() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(
        250, Map.of(FIRST_WINDOW_START, entries(150), SECOND_WINDOW_START, entries(100)));

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .containsExactly(
            new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0),
            new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 100),
            new Slice(SECOND_WINDOW_START, FIRST_WINDOW_START, 0),
            new Slice(SECOND_WINDOW_START, FIRST_WINDOW_START, 100));
  }

  @Test
  void reads_past_an_empty_window_in_the_middle_of_the_history() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(2, Map.of(FIRST_WINDOW_START, entries(1), THIRD_WINDOW_START, entries(1)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .extracting(Slice::from)
        .containsExactly(FIRST_WINDOW_START, SECOND_WINDOW_START, THIRD_WINDOW_START);
    assertThat(summary.status()).isEqualTo(ContratosMenoresImportStatus.COMPLETE);
  }

  @Test
  void resumes_from_the_stored_cursor_rather_than_from_today() {
    resumesFrom(LocalDate.of(2025, 3, 10));
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, Map.of(LocalDate.of(2024, 12, 11), entries(1)));

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .containsExactly(new Slice(LocalDate.of(2024, 12, 11), LocalDate.of(2025, 3, 10), 0));
  }

  @Test
  void stamps_the_covered_through_instant_when_it_creates_the_state_row() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(0, Map.of());

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(createdState.get())
        .isEqualTo(ContratosMenoresImportState.startedAt(ORGANO_ID, T_ZERO));
  }

  @Test
  void never_creates_another_state_row_when_it_resumes() {
    resumesFrom(LocalDate.of(2025, 3, 10));
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(0, Map.of());

    walk().run(RUN_ID, organo(), () -> true);

    verify(importStates, never()).insert(any());
  }

  @Test
  void leaves_the_organo_complete_when_the_stored_count_reaches_the_sources_own() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, Map.of(FIRST_WINDOW_START, entries(1)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(1, 0));
    verify(importStates).updateState(ORGANO_ID, ContratosMenoresImportStatus.COMPLETE);
  }

  @Test
  void leaves_the_organo_incomplete_when_it_reaches_the_configured_history_floor() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(99, Map.of());
    LocalDate floor = LocalDate.of(2026, 1, 1);

    ContratosMenoresImportSummary summary = walkFrom(floor).run(RUN_ID, organo(), () -> true);

    assertThat(summary.status()).isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
    assertThat(requestedSlices)
        .extracting(Slice::from)
        .containsExactly(FIRST_WINDOW_START, SECOND_WINDOW_START, floor);
    verify(importStates, never()).updateState(any(), any());
  }

  @Test
  void completes_after_one_request_when_the_organo_published_nothing() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(0, Map.of());

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(0, 0));
    assertThat(requestedSlices).containsExactly(new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0));
  }

  @Test
  void keeps_walking_when_the_sources_count_grows_while_the_walk_runs() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    AtomicLong recordsTotal = new AtomicLong(1);
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          requestedSlices.add(
              new Slice(from, invocation.getArgument(2), invocation.getArgument(3)));
          if (FIRST_WINDOW_START.equals(from)) {
            // A publication arrives while the first window is being read, so the figure that
            // window is judged against is no longer the one the walk set out with.
            recordsTotal.set(2);
            return new ContratoMenorSourcePage(entries(1), recordsTotal.get());
          }
          List<ContratoMenorSourceEntry> published =
              SECOND_WINDOW_START.equals(from) ? entries(1) : List.of();
          return new ContratoMenorSourcePage(published, recordsTotal.get());
        });

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(2, 0));
  }

  @Test
  void advances_the_run_after_every_batch() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    walk().run(RUN_ID, organo(), () -> true);

    verify(importRuns).advance(RUN_ID, ORGANO_ID, 100, 0);
    verify(importRuns).advance(RUN_ID, ORGANO_ID, 50, 0);
  }

  // Asserted in order, because order is the whole claim: verifying the two calls separately passes
  // just as well when the window's end and its start are written the wrong way round, and writing
  // the start first is exactly the bug that makes a resumption skip a half-paged window.
  @Test
  void holds_the_cursor_at_the_window_end_until_that_window_is_fully_paged() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    cursorWritesAreRecorded();
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(cursorWrites).containsExactly(T_ZERO_DAY, FIRST_WINDOW_START);
  }

  @Test
  void lets_the_source_failure_out_without_marking_the_organo_loaded() {
    neverStarted();
    runIsLive();
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenThrow(new ContratoMenorSourceUnavailableException("source is down"));
    ImportOrganoContratosMenores walk = walk();
    OrganoDeContratacion organo = organo();

    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(() -> walk.run(RUN_ID, organo, () -> true));

    verify(importStates, never()).updateState(any(), any());
  }

  // The cursor is asserted as well as the summary, because the two bookkeeping writes are ordered:
  // the cursor moves first and the run is advanced after it. Advancing first would leave a batch
  // whose run failed with no resumption point either, and only this assertion can tell them apart.
  @Test
  void carries_on_when_the_batch_progress_cannot_be_recorded() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    cursorWritesAreRecorded();
    sourcePublishes(1, Map.of(FIRST_WINDOW_START, entries(1)));
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .advance(any(), any(), anyInt(), anyInt());

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(1, 0));
    assertThat(cursorWrites).containsExactly(FIRST_WINDOW_START);
  }

  // The other half of the same guarantee. The cursor write is this walk's own per-batch hook rather
  // than the shared read's business, and it runs inside the try the advance does — so a hook that
  // throws costs the import nothing either. Outside that try it would break an initial import on a
  // transient state-row failure, with the batch's contracts already committed.
  @Test
  void carries_on_when_the_cursor_cannot_be_moved() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, Map.of(FIRST_WINDOW_START, entries(1)));
    doThrow(new IllegalStateException("the state row is unreachable"))
        .when(importStates)
        .updateCursorDate(any(), any());

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(1, 0));
  }

  @Test
  void reports_nothing_added_when_every_publication_is_already_stored() {
    neverStarted();
    runIsLive();
    stored.set(1);
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> new UpsertCounts(0, sizeOfBatch(invocation)));
    when(contratos.countByOrganoId(ORGANO_ID)).thenAnswer(invocation -> stored.get());
    sourcePublishes(1, Map.of(FIRST_WINDOW_START, entries(1)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.complete(0, 1));
  }

  // The page is handed on as the source published it, awardee included. Anything the walk narrowed
  // here would be narrowed for good: the awardee is on no stored contract, so the derivation has
  // only what this call carries.
  @Test
  void hands_each_page_to_the_store_under_the_awarding_organo_as_the_source_published_it() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, Map.of(FIRST_WINDOW_START, entries(1)));

    walk().run(RUN_ID, organo(), () -> true);

    assertThat(handedToTheStore).isEqualTo(entries(1));
  }

  // The guard is asked twice a batch, and this is the ask that matters: the run went quiet while
  // its page was in flight, so the answer is read before the progress write renews it. Asking only
  // at the top of the loop would read a liveness the walk had just written itself.
  @Test
  void stops_without_recording_progress_when_the_guard_goes_mid_batch() {
    neverStarted();
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true).thenReturn(false);
    // Only the upsert: the walk stops at the second ask, before it tests its stored count.
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> new UpsertCounts(sizeOfBatch(invocation), 0));
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary)
        .isEqualTo(ContratosMenoresImportSummary.stopped(100, 0, StopReason.GUARD_LOST));
    assertThat(requestedSlices).containsExactly(new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0));
    // The batch's contracts stand; the cursor stays where the batch before it left it, so the
    // window is re-read whole on resumption rather than half-skipped.
    verify(importRuns, never()).advance(any(), any(), anyInt(), anyInt());
    verify(importStates, never()).updateCursorDate(any(), any());
    verify(importStates, never()).updateState(any(), any());
  }

  @Test
  void stops_before_the_next_batch_when_the_guard_goes_between_two_of_them() {
    neverStarted();
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true).thenReturn(true).thenReturn(false);
    // Only the upsert: the walk stops at the top of the next batch, before it tests its count.
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> new UpsertCounts(sizeOfBatch(invocation), 0));
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary)
        .isEqualTo(ContratosMenoresImportSummary.stopped(100, 0, StopReason.GUARD_LOST));
    assertThat(requestedSlices).containsExactly(new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0));
    verify(importRuns).advance(RUN_ID, ORGANO_ID, 100, 0);
    verify(importStates, never()).updateState(any(), any());
  }

  @Test
  void reads_nothing_at_all_when_the_run_is_already_gone() {
    neverStarted();
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(false);

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary)
        .isEqualTo(ContratosMenoresImportSummary.stopped(0, 0, StopReason.GUARD_LOST));
    verifyNoInteractions(contratoMenorSource, batch, contratos);
  }

  // The mark is withdrawn while the first batch is in flight. The check sits at the very bottom of
  // the batch, so that batch is stored *and* recorded before the walk gives up — anything else
  // would leave contracts committed with neither a cursor nor a count to show for them.
  @Test
  void stops_at_the_batch_boundary_when_the_organo_stops_being_eligible() {
    neverStarted();
    runIsLive();
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> new UpsertCounts(sizeOfBatch(invocation), 0));
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), unmarkedAfterBatch(1));

    assertThat(summary)
        .isEqualTo(ContratosMenoresImportSummary.stopped(100, 0, StopReason.UNMARKED));
    assertThat(requestedSlices).containsExactly(new Slice(FIRST_WINDOW_START, T_ZERO_DAY, 0));
    verify(importRuns).advance(RUN_ID, ORGANO_ID, 100, 0);
  }

  // The withdrawal lands on the batch that exhausts the window, which is the batch after which the
  // walk would have tested its stored count and could have marked the Órgano loaded. It must not:
  // an Órgano marked loaded on a history it was told to stop reading is never resumed.
  @Test
  void leaves_the_organo_incomplete_when_it_is_unmarked_on_the_batch_that_ends_the_window() {
    neverStarted();
    runIsLive();
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> new UpsertCounts(sizeOfBatch(invocation), 0));
    cursorWritesAreRecorded();
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), unmarkedAfterBatch(2));

    assertThat(summary)
        .isEqualTo(ContratosMenoresImportSummary.stopped(150, 0, StopReason.UNMARKED));
    assertThat(cursorWrites).containsExactly(T_ZERO_DAY, FIRST_WINDOW_START);
    verify(importStates, never()).updateState(any(), any());
    verifyNoInteractions(contratos);
  }

  @Test
  void asks_whether_the_organo_is_still_eligible_once_per_batch() {
    neverStarted();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, Map.of(FIRST_WINDOW_START, entries(150)));
    AtomicInteger asks = new AtomicInteger();

    walk().run(RUN_ID, organo(), () -> asks.incrementAndGet() > 0);

    assertThat(asks).hasValue(2);
  }

  // A walk resuming from a cursor an earlier one left at the floor has read every window there is.
  // Asking the source for the day-wide window that remains cannot converge a count that the whole
  // history did not, so it asks for nothing.
  @Test
  void asks_the_source_for_nothing_when_it_resumes_from_the_history_floor() {
    resumesFrom(HISTORY_FLOOR);

    ContratosMenoresImportSummary summary = walk().run(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresImportSummary.incomplete(0, 0));
    verifyNoInteractions(contratoMenorSource, batch, contratos, importRuns);
  }

  private ImportOrganoContratosMenores walk() {
    return walkFrom(HISTORY_FLOOR);
  }

  private ImportOrganoContratosMenores walkFrom(LocalDate historyFloor) {
    return new ImportOrganoContratosMenores(
        new ReadContratosMenoresWindow(contratoMenorSource, batch, importRuns),
        contratos,
        importStates,
        clock,
        new ContratosMenoresImportConfiguration(historyFloor));
  }

  /** Answers true until the boundary that follows batch {@code batch}, where the mark is gone. */
  private static BooleanSupplier unmarkedAfterBatch(int batch) {
    AtomicInteger boundaries = new AtomicInteger();
    return () -> boundaries.incrementAndGet() < batch;
  }

  private void neverStarted() {
    when(importStates.findByOrganoId(ORGANO_ID)).thenReturn(Optional.empty());
    when(clock.instant()).thenReturn(T_ZERO);
    when(importStates.insert(any()))
        .thenAnswer(invocation -> {
          ContratosMenoresImportState state = invocation.getArgument(0);
          createdState.set(state);
          return state;
        });
  }

  private void resumesFrom(LocalDate cursorDate) {
    when(importStates.findByOrganoId(ORGANO_ID))
        .thenReturn(
            Optional.of(
                new ContratosMenoresImportState(
                    ORGANO_ID, ContratosMenoresImportStatus.INCOMPLETE, cursorDate, T_ZERO, null)));
  }

  private void runIsLive() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
  }

  private void cursorWritesAreRecorded() {
    doAnswer(invocation -> cursorWrites.add(invocation.getArgument(1)))
        .when(importStates)
        .updateCursorDate(eq(ORGANO_ID), any());
  }

  /** Serves {@code published} keyed by the window's start date, paged as the source pages. */
  private void sourcePublishes(
      long recordsTotal, Map<LocalDate, List<ContratoMenorSourceEntry>> published) {
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          int offset = invocation.getArgument(3);
          int pageSize = invocation.getArgument(4);
          requestedSlices.add(new Slice(from, invocation.getArgument(2), offset));
          List<ContratoMenorSourceEntry> window = published.getOrDefault(from, List.of());
          int start = Math.min(offset, window.size());
          return new ContratoMenorSourcePage(
              window.subList(start, Math.min(start + pageSize, window.size())), recordsTotal);
        });
  }

  private void storeAcceptsEverything() {
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> {
          List<ContratoMenorSourceEntry> page = invocation.getArgument(0);
          handedToTheStore.addAll(page);
          stored.addAndGet(page.size());
          return new UpsertCounts(page.size(), 0);
        });
    when(contratos.countByOrganoId(ORGANO_ID)).thenAnswer(invocation -> stored.get());
  }

  private static int sizeOfBatch(InvocationOnMock invocation) {
    return invocation.<Collection<?>>getArgument(0).size();
  }

  private static OrganoDeContratacion organo() {
    return new OrganoDeContratacion(
        ORGANO_ID, SOURCE_KEY, "Axencia Turismo de Galicia", true, true, null);
  }

  private static List<ContratoMenorSourceEntry> entries(int count) {
    return LongStream.rangeClosed(1, count)
        .mapToObj(
            sourceId ->
                new ContratoMenorSourceEntry(
                    sourceId,
                    LocalDate.of(2026, 6, 1),
                    "Obxecto %d".formatted(sourceId),
                    new Money(new BigDecimal("100.00")),
                    "1 mes",
                    "ACME SL",
                    "B12345678"))
        .toList();
  }
}
