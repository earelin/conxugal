package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.db.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.contrato.ContratoMenorSource;
import gal.conxugal.domain.contrato.ContratoMenorSourceEntry;
import gal.conxugal.domain.contrato.ContratoMenorSourcePage;
import gal.conxugal.domain.contrato.ContratoMenorSourceUnavailableException;
import gal.conxugal.domain.contrato.ContratosMenoresRefreshSummary;
import gal.conxugal.domain.contrato.RefreshOrganoContratosMenores;
import gal.conxugal.domain.contrato.StopReason;
import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.time.Clock;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * One loaded Órgano's refresh against a real PostgreSQL and a stubbed source: where the refresh
 * mark ends up on each of the walk's endings, and what a run measured from it covers.
 *
 * <p><strong>{@code transactional = false}</strong>, for the reason the initial walk's suite beside
 * this one is: the point is which writes commit separately. The default would join the contracts
 * and the mark into one transaction the test rolled back, and every claim here would hold for a
 * reason production does not have.
 *
 * <p>The clock is <strong>mutable</strong>, unlike that suite's fixed one. A refresh stamps its own
 * instant, and an implementation stamping the walk's end rather than its start is indistinguishable
 * from a correct one while time does not move.
 *
 * <p>The stubbed source serves whatever falls inside the window it is asked for, rather than a
 * fixed answer per window start. Two consecutive refreshes ask for <em>different</em> windows — the
 * second measures from the mark the first wrote — so a stub keyed by window start would answer the
 * second one nothing and the idempotence claims below would pass vacuously.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganoContratosMenoresRefreshIntegrationTest implements TestPropertyProvider {

  private static final String SOURCE_KEY = "242";

  /** When the Órgano's initial import took its first window, months before any refresh. */
  private static final Instant COVERED_THROUGH = Instant.parse("2026-01-15T09:00:00Z");

  /** The Órgano's last clean refresh: a fortnight before the one under test. */
  private static final Instant PREVIOUS_REFRESH = Instant.parse("2026-07-23T09:00:00Z");

  /** A year before the refresh under test, for the Órgano the scheduler never reached. */
  private static final Instant REFRESHED_LAST_YEAR = Instant.parse("2025-08-06T09:00:00Z");

  /** When the refresh under test starts. Madrid is two hours ahead in August, so it is the 6th. */
  private static final Instant NOW = Instant.parse("2026-08-06T09:00:00Z");

  /** {@code PREVIOUS_REFRESH} less the thirty-day lookback the deployed configuration binds. */
  private static final LocalDate FLOOR_FROM_PREVIOUS_REFRESH = LocalDate.of(2026, 6, 23);

  /** {@code COVERED_THROUGH} less that same lookback, for an Órgano never refreshed. */
  private static final LocalDate FLOOR_FROM_COVERED_THROUGH = LocalDate.of(2025, 12, 16);

  /** Recent enough to fall inside every window any of these refreshes asks for. */
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 8, 1);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
  private final List<ContratoMenorSourceEntry> published = new ArrayList<>();
  private final List<LocalDate> readWindows = new ArrayList<>();
  private LocalDate unreachableWindow;
  private boolean theWalkTakesTime;

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @MockBean(Clock.class)
  Clock clock() {
    return now::get;
  }

  @MockBean(ContratoMenorSource.class)
  ContratoMenorSource contratoMenorSourceMock() {
    return mock(ContratoMenorSource.class);
  }

  @Inject
  RefreshOrganoContratosMenores refreshContratosMenores;

  @Inject
  ContratoMenorSource contratoMenorSource;

  @Inject
  ImportRunRepository importRuns;

  @Inject
  ContratosMenoresImportStateRepository importStates;

  @BeforeEach
  void publishOneContractInsideEveryRecentWindow() {
    now.set(NOW);
    published.clear();
    published.add(entry(1, "Servizos eléctricos", "3630.00"));
    readWindows.clear();
    unreachableWindow = null;
    theWalkTakesTime = false;
    when(contratoMenorSource.fetchPage(eq(SOURCE_KEY), any(), any(), anyInt(), anyInt()))
        .thenAnswer(invocation -> {
          LocalDate from = invocation.getArgument(1);
          if (from.equals(unreachableWindow)) {
            throw new ContratoMenorSourceUnavailableException("source is down");
          }
          readWindows.add(from);
          if (theWalkTakesTime) {
            now.set(NOW.plusSeconds(600));
          }
          LocalDate to = invocation.getArgument(2);
          List<ContratoMenorSourceEntry> window =
              published.stream().filter(entry -> within(entry, from, to)).toList();
          return new ContratoMenorSourcePage(window, window.size());
        });
  }

  @AfterEach
  void cleanUp() throws Exception {
    try (Connection connection = rawConnection()) {
      DatabaseCleanup.truncateAllTables(connection);
    }
  }

  // The mark is the instant the walk began, not the one it ended at: a refresh reads the source
  // while publications keep arriving, and a mark taken at the end would put everything published
  // mid-walk below the next run's floor and in reach of nothing.
  @Test
  void clean_refresh_writes_the_instant_the_walk_began() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    theWalkTakesTime = true;

    ContratosMenoresRefreshSummary summary = refresh(organoId);

    assertThat(summary).isEqualTo(ContratosMenoresRefreshSummary.clean(1, 0));
    assertThat(readWindows).containsExactly(FLOOR_FROM_PREVIOUS_REFRESH);
    assertThat(now.get()).isEqualTo(NOW.plusSeconds(600));
    assertThat(storedMarks(organoId).refreshedThrough()).isEqualTo(NOW);
  }

  // A refresh leaves the Órgano exactly as loaded as it found it, and the resumption point an
  // interrupted initial import restarts from is not a refresh's to move.
  @Test
  void refresh_leaves_the_status_the_cursor_and_the_covered_instant_untouched() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    cursorLeftAt(LocalDate.of(2026, 3, 4));

    refresh(organoId);

    Table states = importStateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states).row(0).value("organo_id").isEqualTo(organoId.value());
    assertThat(states).row(0).value("state").isEqualTo("COMPLETE");
    assertThat(states).row(0).value("cursor_date").isEqualTo(LocalDate.of(2026, 3, 4));
    assertThat(storedMarks(organoId).coveredThrough()).isEqualTo(COVERED_THROUGH);
  }

  @Test
  void refresh_cut_off_by_the_source_leaves_the_mark_where_it_was() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);

    interruptedRefreshOf(organoId);

    assertThat(storedMarks(organoId).refreshedThrough()).isEqualTo(PREVIOUS_REFRESH);
  }

  // Which is what makes an interruption cost freshness rather than data: the next run reads the
  // same period from the same floor, and re-reading a publication refreshes it in place.
  @Test
  void refresh_after_an_interrupted_one_reads_the_same_period_from_the_same_floor()
      throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    interruptedRefreshOf(organoId);
    readWindows.clear();

    ContratosMenoresRefreshSummary second = refresh(organoId);

    assertThat(readWindows).containsExactly(FLOOR_FROM_PREVIOUS_REFRESH);
    assertThat(second).isEqualTo(ContratosMenoresRefreshSummary.clean(1, 0));
  }

  @Test
  void refresh_cut_off_by_an_unmark_leaves_the_mark_where_it_was() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    ImportRunId runId = claim(organoId);

    ContratosMenoresRefreshSummary summary =
        refreshContratosMenores.refresh(runId, organo(organoId), () -> false);
    importRuns.complete(runId, ImportRunState.PARTIALLY_SUCCEEDED, 0, 0);

    assertThat(summary.stoppedBy()).isEqualTo(StopReason.UNMARKED);
    assertThat(contratoTable()).hasNumberOfRows(1);
    assertThat(storedMarks(organoId).refreshedThrough()).isEqualTo(PREVIOUS_REFRESH);
  }

  // The fallback is what makes an Órgano's first refresh after its initial import cover everything
  // published while that import was walking, which for a large publisher is days.
  @Test
  void organo_never_refreshed_measures_its_floor_from_the_covered_instant() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(null);

    refresh(organoId);

    assertThat(readWindows).last().isEqualTo(FLOOR_FROM_COVERED_THROUGH);
    assertThat(storedMarks(organoId).refreshedThrough()).isEqualTo(NOW);
  }

  /**
   * The scheduler having been down, or a long initial import elsewhere having held the guard, for a
   * year: one refresh covers the whole gap, in as many windows as it takes. This is the durable
   * half of the property the scheduler's own database-free tests delegate here.
   */
  @Test
  void year_long_gap_is_covered_in_one_refresh_by_as_many_windows_as_it_takes() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(REFRESHED_LAST_YEAR);

    ContratosMenoresRefreshSummary summary = refresh(organoId);

    assertThat(readWindows)
        .containsExactly(
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 2, 9),
            LocalDate.of(2025, 11, 12),
            LocalDate.of(2025, 8, 15),
            LocalDate.of(2025, 7, 7));
    assertThat(summary.stoppedBy()).isNull();
    assertThat(storedMarks(organoId).refreshedThrough()).isEqualTo(NOW);
  }

  @Test
  void two_refreshes_over_unchanged_publications_store_the_same_set() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    refresh(organoId);
    final UUID identityBeforeTheSecondRefresh = storedIdentityOf(1);

    ContratosMenoresRefreshSummary second = refresh(organoId);

    assertThat(second).isEqualTo(ContratosMenoresRefreshSummary.clean(0, 1));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos");
    assertThat(contratos).row(0).value("amount").isEqualTo(new BigDecimal("3630.00"));
    assertThat(storedIdentityOf(1)).isEqualTo(identityBeforeTheSecondRefresh);
  }

  // The lookback margin's whole purpose: the source offers no *changed since* facility, so a
  // correction is discoverable only by re-reading the period it falls in.
  @Test
  void correction_published_inside_the_window_is_picked_up() throws Exception {
    OrganoId organoId = loadedOrganoRefreshedThrough(PREVIOUS_REFRESH);
    refresh(organoId);
    published.set(0, entry(1, "Servizos eléctricos, corrixido", "4000.00"));

    refresh(organoId);

    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Servizos eléctricos, corrixido");
    assertThat(contratos).row(0).value("amount").isEqualTo(new BigDecimal("4000.00"));
  }

  /** A refresh that lost the source, settled as the orchestrator would settle it. */
  private void interruptedRefreshOf(OrganoId organoId) {
    unreachableWindow = FLOOR_FROM_PREVIOUS_REFRESH;
    ImportRunId runId = claim(organoId);
    assertThatExceptionOfType(ContratoMenorSourceUnavailableException.class)
        .isThrownBy(() -> refreshContratosMenores.refresh(runId, organo(organoId), () -> true));
    importRuns.complete(runId, ImportRunState.FAILED, 0, 0);
    unreachableWindow = null;
  }

  private ContratosMenoresRefreshSummary refresh(OrganoId organoId) {
    ImportRunId runId = claim(organoId);
    ContratosMenoresRefreshSummary summary =
        refreshContratosMenores.refresh(runId, organo(organoId), () -> true);
    importRuns.complete(runId, ImportRunState.SUCCEEDED, 0, 0);
    return summary;
  }

  /** An Órgano whose initial import completed, which is the only kind that is refreshed at all. */
  private OrganoId loadedOrganoRefreshedThrough(@Nullable Instant refreshMark) throws SQLException {
    OrganoId organoId = insertOrgano();
    importStates.insert(ContratosMenoresImportState.startedAt(organoId, COVERED_THROUGH));
    importStates.updateState(organoId, ContratosMenoresImportStatus.COMPLETE);
    if (refreshMark != null) {
      importStates.updateRefreshedThrough(organoId, refreshMark);
    }
    return organoId;
  }

  /** The resumption point the Órgano's own initial walk left behind when it finished. */
  private static void cursorLeftAt(LocalDate cursorDate) throws SQLException {
    execute("UPDATE contrato_menor_import_state SET cursor_date = ?", cursorDate);
  }

  private static boolean within(ContratoMenorSourceEntry entry, LocalDate from, LocalDate to) {
    LocalDate publishedOn = Objects.requireNonNull(entry.publicationDate());
    return !publishedOn.isBefore(from) && !publishedOn.isAfter(to);
  }

  private ImportRunId claim(OrganoId organoId) {
    return importRuns
        .claim(Importer.CONTRATOS_MENORES, List.of(organoId))
        .orElseThrow(() -> new IllegalStateException("the import guard was already held"));
  }

  private static OrganoDeContratacion organo(OrganoId organoId) {
    return new OrganoDeContratacion(
        organoId, SOURCE_KEY, "Axencia Turismo de Galicia", true, true, null);
  }

  private static ContratoMenorSourceEntry entry(long sourceId, String obxecto, String amount) {
    return new ContratoMenorSourceEntry(
        sourceId,
        PUBLISHED_ON,
        obxecto,
        new Money(new BigDecimal(amount)),
        "1 mes",
        "ACME SL",
        "B12345678");
  }

  private static Table contratoTable() {
    return table("contrato_menor", "source_id");
  }

  private static Table importStateTable() {
    return table("contrato_menor_import_state", "organo_id");
  }

  // Off the container rather than the injected DataSource: with no ambient transaction every write
  // under test really commits, and these assertions are about what it committed.
  private static Table table(String name, String order) {
    return AssertDbConnectionFactory.of(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(order)})
        .build();
  }

  private record StoredMarks(Instant coveredThrough, @Nullable Instant refreshedThrough) {}

  // Read through JDBC rather than asserted on the table, because AssertJ DB compares a timestamptz
  // against the session time zone and the claims here are about the instants. Both columns come
  // back together so the query stays a literal.
  private static StoredMarks storedMarks(OrganoId organoId) throws SQLException {
    String sql =
        "SELECT covered_through, refreshed_through FROM contrato_menor_import_state"
            + " WHERE organo_id = ?";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, organoId.value());
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("No state stored for Órgano %s".formatted(organoId));
        }
        return new StoredMarks(
            instantAt(resultSet, "covered_through"), instantAt(resultSet, "refreshed_through"));
      }
    }
  }

  private static @Nullable Instant instantAt(ResultSet resultSet, String column)
      throws SQLException {
    Timestamp stored = resultSet.getTimestamp(column);
    return stored == null ? null : stored.toInstant().truncatedTo(ChronoUnit.MILLIS);
  }

  private static UUID storedIdentityOf(long sourceId) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM contrato_menor WHERE source_id = ?")) {
      statement.setLong(1, sourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "No contract stored for source id %d".formatted(sourceId));
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  private static OrganoId insertOrgano() throws SQLException {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active, importable)"
            + " VALUES (uuidv7(), ?, ?, TRUE, TRUE) RETURNING id";
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, SOURCE_KEY);
      statement.setString(2, "Axencia Turismo de Galicia");
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  private static void execute(String sql, Object... parameters) throws SQLException {
    try (Connection connection = rawConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  private static Connection rawConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }
}
