package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.LongStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshOrganoContratosMenoresTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final ImportRunId RUN_ID = new ImportRunId(UUID.randomUUID());
  private static final String SOURCE_KEY = "242";

  private static final Duration LOOKBACK = Duration.ofDays(30);
  private static final LocalDate HISTORY_FLOOR = LocalDate.of(2018, 1, 1);

  /** When the refresh runs. Madrid is two hours ahead of UTC in August, so the day is the 6th. */
  private static final Instant T_ONE = Instant.parse("2026-08-06T09:00:00Z");

  private static final LocalDate TODAY = LocalDate.of(2026, 8, 6);

  /** A fortnight before the refresh, so its floor lands thirty days before that. */
  private static final Instant PREVIOUS_REFRESH = Instant.parse("2026-07-23T09:00:00Z");

  private static final LocalDate FLOOR_FROM_PREVIOUS_REFRESH = LocalDate.of(2026, 6, 23);

  /** When the Órgano's initial import took its first window, three weeks before that refresh. */
  private static final Instant COVERED_THROUGH = Instant.parse("2026-07-02T09:00:00Z");

  private static final LocalDate FLOOR_FROM_COVERED_THROUGH = LocalDate.of(2026, 6, 2);

  private static final Instant REFRESHED_LAST_YEAR = Instant.parse("2025-08-06T09:00:00Z");

  @Mock
  private ContratoMenorSource contratoMenorSource;

  @Mock
  private StoreContratosMenoresBatch batch;

  @Mock
  private ContratosMenoresImportStateRepository importStates;

  @Mock
  private ImportRunRepository importRuns;

  @Mock
  private Clock clock;

  private final AtomicReference<Instant> now = new AtomicReference<>(T_ONE);
  private final List<Slice> requestedSlices = new ArrayList<>();
  /** Set by the one test that needs the clock to move while the walk is running. */
  private boolean theWalkTakesTenMinutes;

  /** One call the source port received, which is what the walk's shape is asserted on. */
  private record Slice(LocalDate from, LocalDate to, int offset) {}

  /**
   * Every refresh reads the clock, so it is stubbed for every test. It answers {@link #T_ONE} until
   * something moves it, which is what {@link #theWalkTakesTenMinutes} does from inside the
   * source: a fixed clock cannot tell a mark stamped at the walk's start from one stamped at its
   * end.
   */
  @BeforeEach
  void theClockRuns() {
    when(clock.instant()).thenAnswer(invocation -> now.get());
  }

  @Test
  void reads_one_window_from_today_back_to_the_floor_its_refresh_mark_decides() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices).containsExactly(new Slice(FLOOR_FROM_PREVIOUS_REFRESH, TODAY, 0));
  }

  // The whole of the fallback: an Órgano that has never had a clean refresh measures from the
  // instant its history is covered through, so its first refresh covers everything published while
  // its initial import was walking — days, for a large publisher.
  @Test
  void organo_never_refreshed_measures_its_floor_from_the_covered_instant() {
    neverRefreshed();
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices).containsExactly(new Slice(FLOOR_FROM_COVERED_THROUGH, TODAY, 0));
  }

  @Test
  void subtracts_the_lookback_it_is_configured_with() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refreshWith(Duration.ofDays(1)).refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .extracting(Slice::from)
        .containsExactly(LocalDate.of(2026, 7, 22));
  }

  // The scheduler having been down, or a long import elsewhere having held the guard, costs
  // freshness and never data: one refresh takes as many windows as the gap needs.
  @Test
  void covers_year_long_gap_in_one_refresh_by_as_many_windows_as_it_takes() {
    refreshedThrough(REFRESHED_LAST_YEAR);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .extracting(Slice::from)
        .containsExactly(
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 2, 9),
            LocalDate.of(2025, 11, 12),
            LocalDate.of(2025, 8, 15),
            LocalDate.of(2025, 7, 7));
  }

  // The next window ends where this one began, exactly as the initial walk overlaps: the source was
  // never measured saying whether it counts its end date as within the window, so the two overlap
  // by that day rather than assume.
  @Test
  void steps_back_in_windows_that_overlap_by_their_boundary_day() {
    refreshedThrough(Instant.parse("2026-01-06T09:00:00Z"));
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .containsExactly(
            new Slice(LocalDate.of(2026, 5, 9), TODAY, 0),
            new Slice(LocalDate.of(2026, 2, 9), LocalDate.of(2026, 5, 9), 0),
            new Slice(LocalDate.of(2025, 12, 7), LocalDate.of(2026, 2, 9), 0));
  }

  // Days, not months, and for the reason the initial walk steps in days: a window stepped back by
  // months lands on one whose own three months end before it began whenever the month it lands in
  // is shorter, and the source refuses that window as over-wide.
  @Test
  void asks_for_windows_no_wider_than_the_shortest_three_calendar_months() {
    refreshedThrough(REFRESHED_LAST_YEAR);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices)
        .allSatisfy(
            slice ->
                assertThat(slice.from())
                    .isAfterOrEqualTo(slice.to().minusMonths(3))
                    .isBefore(slice.to()));
  }

  @Test
  void answers_what_it_added_and_what_it_refreshed() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAddsTheFirstOfEachPageAndRefreshesTheRest();
    sourcePublishes(Map.of(FLOOR_FROM_PREVIOUS_REFRESH, entries(3)));

    ContratosMenoresRefreshSummary summary = refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresRefreshSummary.clean(1, 2));
  }

  /**
   * Every window's counts, not the last one's. A walk covering a year reads five windows, and an
   * accumulator that assigned rather than added would report whatever the oldest one found — which
   * for a refresh is usually nothing, so the summary the orchestrator settles on, and the figures
   * an administrator reads off the run, would silently under-report.
   */
  @Test
  void adds_up_what_every_window_stored_rather_than_the_last_one_alone() {
    refreshedThrough(REFRESHED_LAST_YEAR);
    runIsLive();
    storeAddsTheFirstOfEachPageAndRefreshesTheRest();
    sourcePublishes(
        Map.of(
            LocalDate.of(2026, 5, 9), entries(2),
            LocalDate.of(2025, 11, 12), entries(3)));

    ContratosMenoresRefreshSummary summary = refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(summary).isEqualTo(ContratosMenoresRefreshSummary.clean(2, 3));
  }

  /**
   * A refresh mark ahead of the reading clock by more than the lookback — clock skew between two
   * nodes, or a lookback configured down to minutes — would put the floor past today and have the
   * walk ask for a window whose start is after its end. The source answers one it cannot parse with
   * a bare {@code 500}, so the Órgano would be recorded failed for what reads as an outage.
   */
  @Test
  void clamps_the_floor_to_today_when_the_refresh_mark_is_ahead_of_the_clock() {
    refreshedThrough(T_ONE.plus(Duration.ofDays(365)));
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    ContratosMenoresRefreshSummary summary = refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(requestedSlices).containsExactly(new Slice(TODAY, TODAY, 0));
    assertThat(summary).isEqualTo(ContratosMenoresRefreshSummary.clean(0, 0));
  }

  /**
   * The instant the walk <em>began</em>, not the one it ended at and not a window boundary. A clock
   * that moves between reads is what tells the first two apart: an implementation stamping the end
   * would write the second instant. {@code verify} rather than an assertion because the mark is a
   * void side effect on a collaborator, and it pins the write happening exactly once.
   */
  @Test
  void writes_the_refresh_mark_stamped_at_the_instant_the_walk_began() {
    refreshedThrough(PREVIOUS_REFRESH);
    theWalkTakesTenMinutes = true;
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(now.get()).isEqualTo(T_ONE.plusSeconds(600));
    verify(importStates).updateRefreshedThrough(ORGANO_ID, T_ONE);
  }

  @Test
  void writes_the_refresh_mark_once_however_many_windows_it_took() {
    refreshedThrough(REFRESHED_LAST_YEAR);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());

    refresh().refresh(RUN_ID, organo(), () -> true);

    verify(importStates).updateRefreshedThrough(ORGANO_ID, T_ONE);
  }

  // Everything read up to the failure stands, and the mark is left where it was, so the next run
  // reads the same period from the same floor.
  @Test
  void leaves_the_refresh_mark_alone_when_the_source_becomes_unreachable() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    sourceIsUnreachable();

    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(() -> refresh().refresh(RUN_ID, organo(), () -> true));

    verify(importStates, never()).updateRefreshedThrough(any(), any());
  }

  @Test
  void leaves_the_refresh_mark_alone_when_the_organo_is_unmarked_mid_walk() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of(FLOOR_FROM_PREVIOUS_REFRESH, entries(1)));

    ContratosMenoresRefreshSummary summary =
        refresh().refresh(RUN_ID, organo(), unmarkedAfterBatch(1));

    assertThat(summary)
        .isEqualTo(ContratosMenoresRefreshSummary.stopped(1, 0, StopReason.UNMARKED));
    verify(importStates, never()).updateRefreshedThrough(any(), any());
  }

  @Test
  void leaves_the_refresh_mark_alone_when_the_run_stops_holding_the_guard() {
    refreshedThrough(PREVIOUS_REFRESH);
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(false);

    ContratosMenoresRefreshSummary summary = refresh().refresh(RUN_ID, organo(), () -> true);

    assertThat(summary)
        .isEqualTo(ContratosMenoresRefreshSummary.stopped(0, 0, StopReason.GUARD_LOST));
    verify(importStates, never()).updateRefreshedThrough(any(), any());
    verifyNoInteractions(contratoMenorSource, batch);
  }

  /**
   * Not best-effort, unlike the progress writes inside the page loop. A mark that failed silently
   * would read as a clean refresh while the floor stayed where it was; letting the failure out is
   * what has the orchestrator record the Órgano failed, at the cost of one duplicated read.
   */
  @Test
  void lets_the_failure_to_write_the_refresh_mark_out() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of());
    doThrow(new IllegalStateException("the state row is gone"))
        .when(importStates)
        .updateRefreshedThrough(ORGANO_ID, T_ONE);

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> refresh().refresh(RUN_ID, organo(), () -> true));
  }

  /**
   * The three-state fact is not a refresh's to move, and the resumption point is the initial walk's
   * alone. Neither can be shown by what this class is given — it holds the import state repository
   * for the mark it does write — so both are asserted as interactions that never happen.
   */
  @Test
  void moves_neither_the_import_status_nor_the_resumption_cursor() {
    refreshedThrough(PREVIOUS_REFRESH);
    runIsLive();
    storeAcceptsEverything();
    sourcePublishes(Map.of(FLOOR_FROM_PREVIOUS_REFRESH, entries(2)));

    refresh().refresh(RUN_ID, organo(), () -> true);

    verify(importStates, never()).updateState(any(), any());
    verify(importStates, never()).updateCursorDate(any(), any());
  }

  // The completion that makes an Órgano refreshable is a write on this very row, so no row at all
  // is a contradiction rather than a first import. Inventing a mark for it would silently refresh
  // from a month ago whatever the Órgano actually holds.
  @Test
  void refuses_to_refresh_an_organo_with_no_import_state_at_all() {
    when(importStates.findByOrganoId(ORGANO_ID)).thenReturn(Optional.empty());

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> refresh().refresh(RUN_ID, organo(), () -> true))
        .withMessageContaining("no contratos menores import state");
  }

  private RefreshOrganoContratosMenores refresh() {
    return refreshWith(LOOKBACK);
  }

  private RefreshOrganoContratosMenores refreshWith(Duration lookback) {
    return new RefreshOrganoContratosMenores(
        new ReadContratosMenoresWindow(contratoMenorSource, batch, importRuns),
        importStates,
        clock,
        new ContratosMenoresImportConfiguration(HISTORY_FLOOR, lookback));
  }

  /** Answers true until the boundary that follows batch {@code batch}, where the mark is gone. */
  private static BooleanSupplier unmarkedAfterBatch(int batch) {
    AtomicInteger boundaries = new AtomicInteger();
    return () -> boundaries.incrementAndGet() < batch;
  }

  private void neverRefreshed() {
    refreshedThrough(null);
  }

  /** A loaded Órgano, which is the only kind that is refreshed at all. */
  private void refreshedThrough(@Nullable Instant refreshMark) {
    when(importStates.findByOrganoId(ORGANO_ID))
        .thenReturn(
            Optional.of(
                new ContratosMenoresImportState(
                    ORGANO_ID,
                    ContratosMenoresImportStatus.COMPLETE,
                    null,
                    COVERED_THROUGH,
                    refreshMark)));
  }

  private void runIsLive() {
    when(importRuns.holdsGuard(RUN_ID)).thenReturn(true);
  }

  /** Serves {@code published} keyed by the window's start date, paged as the source pages. */
  private void sourcePublishes(Map<LocalDate, List<ContratoMenorSourceEntry>> published) {
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          int offset = invocation.getArgument(3);
          int pageSize = invocation.getArgument(4);
          requestedSlices.add(new Slice(from, invocation.getArgument(2), offset));
          if (theWalkTakesTenMinutes) {
            now.set(T_ONE.plusSeconds(600));
          }
          List<ContratoMenorSourceEntry> window = published.getOrDefault(from, List.of());
          int start = Math.min(offset, window.size());
          return new ContratoMenorSourcePage(
              window.subList(start, Math.min(start + pageSize, window.size())), window.size());
        });
  }

  private void sourceIsUnreachable() {
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenThrow(new ContratoMenorSourceUnavailableException("source is down"));
  }

  /** Counts split across both columns, so a summary reporting only one of them is visible. */
  private void storeAddsTheFirstOfEachPageAndRefreshesTheRest() {
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> {
          List<ContratoMenorSourceEntry> page = invocation.getArgument(0);
          return page.isEmpty()
              ? new UpsertCounts(0, 0)
              : new UpsertCounts(1, page.size() - 1);
        });
  }

  private void storeAcceptsEverything() {
    when(batch.store(anyList(), eq(ORGANO_ID)))
        .thenAnswer(invocation -> {
          List<ContratoMenorSourceEntry> page = invocation.getArgument(0);
          return new UpsertCounts(page.size(), 0);
        });
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
                    LocalDate.of(2026, 7, 1),
                    "Obxecto %d".formatted(sourceId),
                    new Money(new BigDecimal("100.00")),
                    "1 mes",
                    "ACME SL",
                    "B12345678"))
        .toList();
  }
}
