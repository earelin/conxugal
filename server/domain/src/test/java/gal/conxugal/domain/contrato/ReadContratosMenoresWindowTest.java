package gal.conxugal.domain.contrato;

import static gal.conxugal.domain.contrato.ReadContratosMenoresWindow.PAGE_SIZE;
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

import gal.conxugal.domain.contrato.ReadContratosMenoresWindow.BatchRecorder;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * One window, paged to exhaustion — the middle every walk shares, driven directly rather than
 * through the walk that owns the windows.
 *
 * <p>{@link ImportOrganoContratosMenoresTest} exercises the same loop through the initial import,
 * which is what proves the two compose. What is pinned here is the contract the loop offers its
 * <em>callers</em>: the order of the two guard asks against the batch commit and the progress
 * write, the eligibility ask at the bottom, what the per-batch hook is told, and that a hook or an
 * advance that throws costs the window nothing. A second walk arrives in this class rather than in
 * that one.
 */
@ExtendWith(MockitoExtension.class)
class ReadContratosMenoresWindowTest {

  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final String SOURCE_KEY = "242";
  private static final WalkTarget TARGET = new WalkTarget(RUN_ID, ORGANO_ID, SOURCE_KEY);

  private static final LocalDate WINDOW_START = LocalDate.of(2026, 5, 9);
  private static final LocalDate WINDOW_END = LocalDate.of(2026, 8, 6);

  @Mock
  private ContratoMenorSource contratoMenorSource;

  @Mock
  private StoreContratosMenoresBatch batch;

  @Mock
  private ImportRunRepository importRuns;

  private final List<Slice> requestedSlices = new ArrayList<>();
  private final List<RecordedBatch> recordedBatches = new ArrayList<>();
  private final List<ContratoMenorSourceEntry> handedToTheStore = new ArrayList<>();

  /** One call the source port received, which is what the paging is asserted on. */
  private record Slice(int offset, int pageSize) {}

  /** One invocation of the caller's hook, with everything the loop told it. */
  private record RecordedBatch(
      UpsertCounts counts, LocalDate windowStart, LocalDate windowEnd, boolean lastPage) {}

  @Test
  void pages_the_window_until_the_source_answers_short_page() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));

    read();

    assertThat(requestedSlices)
        .containsExactly(new Slice(0, PAGE_SIZE), new Slice(PAGE_SIZE, PAGE_SIZE));
  }

  @Test
  void answers_what_it_stored_and_the_count_the_source_reported() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(250, entries(150));

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(150, 0, 250, null));
  }

  @Test
  void counts_the_rows_it_refreshed_apart_from_the_ones_it_added() {
    runIsLive();
    when(batch.store(anyList(), eq(ORGANO_ID))).thenReturn(new UpsertCounts(0, 1));
    sourcePublishes(1, entries(1));

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(0, 1, 1, null));
  }

  @Test
  void hands_each_page_to_the_store_under_the_awarding_organo() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, entries(1));

    read();

    assertThat(handedToTheStore).isEqualTo(entries(1));
  }

  // The first guard ask, before anything is fetched: a window handed a run that is already gone
  // costs the source nothing at all.
  @Test
  void reads_nothing_at_all_when_the_run_is_already_gone() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(false);

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(0, 0, 0, StopReason.GUARD_LOST));
    verifyNoInteractions(contratoMenorSource, batch);
  }

  // The second guard ask, and the one it exists for: the run went quiet while its page was in
  // flight, so the answer is read after the batch commits and before the progress write renews it.
  @Test
  void stops_without_recording_progress_when_the_guard_goes_mid_page() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true).thenReturn(false);
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(100, 0, 0, StopReason.GUARD_LOST));
    assertThat(recordedBatches).isEmpty();
    verify(importRuns, never()).advance(any(), any(), anyInt(), anyInt());
  }

  @Test
  void stops_before_the_next_page_when_the_guard_goes_between_two_of_them() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true).thenReturn(true).thenReturn(false);
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(100, 0, 0, StopReason.GUARD_LOST));
    assertThat(requestedSlices).containsExactly(new Slice(0, PAGE_SIZE));
    assertThat(recordedBatches).hasSize(1);
  }

  @Test
  void asks_whether_the_organo_is_still_eligible_once_per_page() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));
    AtomicInteger asks = new AtomicInteger();

    read(() -> asks.incrementAndGet() > 0);

    assertThat(asks).hasValue(2);
  }

  // The eligibility ask sits at the very bottom, so the batch it followed is wholly settled: its
  // contracts committed, its hook run and its counts advanced.
  @Test
  void stops_at_the_page_boundary_when_the_organo_stops_being_eligible() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));

    WindowRead read = read(unmarkedAfterPage(1));

    assertThat(read).isEqualTo(new WindowRead(100, 0, 0, StopReason.UNMARKED));
    assertThat(recordedBatches).hasSize(1);
    verify(importRuns).advance(RUN_ID, ORGANO_ID, 100, 0);
  }

  // Order, not merely occurrence: the caller's hook runs first and the run is advanced after it,
  // which is what leaves a batch whose advance failed with its own record already written.
  @Test
  void records_the_batch_before_it_advances_the_run() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, entries(1));
    List<String> bookkeeping = new ArrayList<>();
    doAnswer(invocation -> bookkeeping.add("run advanced"))
        .when(importRuns)
        .advance(any(), any(), anyInt(), anyInt());

    read(() -> true, (counts, windowStart, windowEnd, lastPage) -> bookkeeping.add("batch"));

    assertThat(bookkeeping).containsExactly("batch", "run advanced");
  }

  // The conservative rule the initial import's cursor depends on: only the page that exhausts the
  // window may be told so, because a walk that recorded a mid-window point would resume inside a
  // window it never finished paging.
  @Test
  void tells_the_hook_which_page_exhausted_the_window() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(150, entries(150));

    read();

    assertThat(recordedBatches)
        .extracting(RecordedBatch::lastPage)
        .containsExactly(false, true);
  }

  @Test
  void tells_the_hook_the_window_it_is_reading_and_what_the_batch_stored() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, entries(1));

    read();

    assertThat(recordedBatches)
        .containsExactly(new RecordedBatch(new UpsertCounts(1, 0), WINDOW_START, WINDOW_END, true));
  }

  // A hook is a caller's business and may fail on its own account. The batch it belongs to has
  // already committed, so the window carries on and the record is what is sacrificed.
  @Test
  void carries_on_when_the_hook_throws() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, entries(1));
    BatchRecorder failing =
        (counts, windowStart, windowEnd, lastPage) -> {
          throw new IllegalStateException("the caller's own record is unreachable");
        };

    WindowRead read = read(() -> true, failing);

    assertThat(read).isEqualTo(new WindowRead(1, 0, 1, null));
  }

  @Test
  void carries_on_when_the_run_cannot_be_advanced() {
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(1, entries(1));
    doThrow(new IllegalStateException("the run record is unreachable"))
        .when(importRuns)
        .advance(any(), any(), anyInt(), anyInt());

    WindowRead read = read();

    assertThat(read).isEqualTo(new WindowRead(1, 0, 1, null));
  }

  // Deliberately let out rather than turned into a stop reason: only the orchestrator can judge
  // what a source failure means to the rest of the run.
  @Test
  void lets_the_source_failure_out_with_everything_it_stored_standing() {
    runIsLive();
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenThrow(new ContratoMenorSourceUnavailableException("source is down"));

    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(this::read);
  }

  private WindowRead read() {
    return read(() -> true);
  }

  private WindowRead read(BooleanSupplier stillEligible) {
    return read(stillEligible, recordingBatches());
  }

  private WindowRead read(BooleanSupplier stillEligible, BatchRecorder recordBatch) {
    return new ReadContratosMenoresWindow(contratoMenorSource, batch, importRuns)
        .read(TARGET, WINDOW_START, WINDOW_END, stillEligible, recordBatch);
  }

  private BatchRecorder recordingBatches() {
    return (counts, windowStart, windowEnd, lastPage) ->
        recordedBatches.add(new RecordedBatch(counts, windowStart, windowEnd, lastPage));
  }

  /** Answers true until the boundary that follows page {@code page}, where the mark is gone. */
  private static BooleanSupplier unmarkedAfterPage(int page) {
    AtomicInteger boundaries = new AtomicInteger();
    return () -> boundaries.incrementAndGet() < page;
  }

  private void runIsLive() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
  }

  /** Serves {@code published} as the source pages it, recording every slice it was asked for. */
  private void sourcePublishes(long recordsTotal, List<ContratoMenorSourceEntry> published) {
    when(contratoMenorSource.fetchPage(
            eq(SOURCE_KEY), eq(WINDOW_START), eq(WINDOW_END), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          int offset = invocation.getArgument(3);
          int pageSize = invocation.getArgument(4);
          requestedSlices.add(new Slice(offset, pageSize));
          int start = Math.min(offset, published.size());
          return new ContratoMenorSourcePage(
              published.subList(start, Math.min(start + pageSize, published.size())), recordsTotal);
        });
  }

  private void storeAcceptsEverything() {
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> {
          List<ContratoMenorSourceEntry> page = invocation.getArgument(0);
          handedToTheStore.addAll(page);
          return new UpsertCounts(page.size(), 0);
        });
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
