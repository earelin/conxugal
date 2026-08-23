package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.LicitacionOutstandingRecord;
import gal.conxugal.domain.licitacion.LicitacionOutstandingRecordRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.jdbc.DataSourceResolver;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The outstanding-record ledger's adapter against a real PostgreSQL.
 *
 * <p>Not transactional, because filing an entry takes a transaction of its own: inside the test's
 * it would not see the row it had not yet committed, which is neither what the walk does nor what
 * the propagation is for.
 *
 * <p>The read is asserted through the port rather than over the table, because reading is what
 * makes a retry possible at all — a retried procedure arrives with no listing entry, and what comes
 * back here is the only place its four published values exist.
 */
@MicronautTest(startApplication = false, transactional = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionOutstandingRecordRepositoryIntegrationTest implements TestPropertyProvider {

  private static final PublicationId PUBLICATION = new PublicationId("822054");
  private static final PublicationId ANOTHER_PUBLICATION = new PublicationId("18747");
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2024, 3, 8);
  private static final LocalDate MODIFIED_ON = LocalDate.of(2026, 8, 20);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionOutstandingRecordRepository ledger;

  @Inject
  LedgerCaller caller;

  @Inject
  DataSource dataSource;

  @Inject
  DataSourceResolver dataSources;

  // The injected DataSource is the connection-context-aware proxy, which has no connection to give
  // outside a transaction — and this class deliberately runs outside one, because the writes under
  // test take transactions of their own.
  private DataSource unproxied;
  private SchemaFixture schema;

  @BeforeEach
  void setUp() {
    unproxied = dataSources.resolve(dataSource);
    schema = SchemaFixture.committing(unproxied);
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(unproxied);
  }

  // The four values the record cannot answer for itself, kept so a retry can store the procedure
  // with no listing entry beside it.
  @Test
  void reads_back_the_four_listing_values_the_entry_was_filed_with() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThat(ledger.outstandingFor(organoId))
        .containsExactly(
            new LicitacionOutstandingRecord(
                PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));
  }

  // A date the listing published in a form nothing could interpret is absent, and stays absent.
  @Test
  void dates_the_listing_never_published_read_back_as_none() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.record(
        organoId, new LicitacionOutstandingRecord(PUBLICATION, null, null, 8, "Formalizado"));

    assertThat(ledger.outstandingFor(organoId))
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.publicationDate()).isNull();
              assertThat(entry.lastModified()).isNull();
            });
  }

  // The walk meets the same failing record on every run, so the ledger must not accumulate.
  @Test
  void recording_the_same_publication_twice_leaves_one_entry() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(PUBLICATION, PUBLISHED_ON, PUBLISHED_ON, 2, "Adxudicado"));
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThat(ledgerTable()).hasNumberOfRows(1);
  }

  // What the listing now publishes is what a retry will store, so a repeat refreshes rather than
  // preserving a reading the source has since restated.
  @Test
  void recording_again_refreshes_what_the_listing_now_publishes() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(PUBLICATION, PUBLISHED_ON, PUBLISHED_ON, 2, "Adxudicado"));
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThat(ledger.outstandingFor(organoId))
        .containsExactly(
            new LicitacionOutstandingRecord(
                PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));
  }

  @Test
  void clearing_one_entry_leaves_the_others() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(ANOTHER_PUBLICATION, null, null, 2, "Adxudicado"));

    ledger.clear(organoId, PUBLICATION);

    assertThat(ledgerTable()).hasNumberOfRows(1);
    assertThat(ledger.outstandingFor(organoId))
        .extracting(LicitacionOutstandingRecord::publicationId)
        .containsExactly(ANOTHER_PUBLICATION);
  }

  // The key is the pair, so one Órgano's retry never drops another's entry for the publication
  // they happen to share.
  @Test
  void clearing_one_organo_entry_leaves_the_same_publication_outstanding_elsewhere()
      throws SQLException {
    OrganoId sergas = schema.organo("sergas");
    OrganoId augas = schema.organo("augas-de-galicia");
    ledger.record(
        sergas,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));
    ledger.record(
        augas,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    ledger.clear(sergas, PUBLICATION);

    assertThat(ledgerTable()).hasNumberOfRows(1);
    assertThat(ledger.outstandingFor(augas)).hasSize(1);
  }

  @Test
  void clearing_an_entry_nobody_filed_is_silent() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.clear(organoId, PUBLICATION);

    assertThat(ledgerTable()).hasNumberOfRows(0);
  }

  // The whole of the completion test: an Órgano becomes complete only if nothing is outstanding.
  @Test
  void has_outstanding_answers_false_for_an_empty_ledger() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    assertThat(ledger.hasOutstanding(organoId)).isFalse();
  }

  @Test
  void has_outstanding_answers_true_with_one_entry() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThat(ledger.hasOutstanding(organoId)).isTrue();
  }

  // Another Órgano's failure never keeps this one from completing.
  @Test
  void has_outstanding_answers_false_while_another_organo_has_entries() throws SQLException {
    OrganoId sergas = schema.organo("sergas");
    OrganoId augas = schema.organo("augas-de-galicia");
    ledger.record(
        sergas,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThat(ledger.hasOutstanding(augas)).isFalse();
  }

  @Test
  void outstanding_for_an_organo_never_answers_another_organo_entries() throws SQLException {
    OrganoId sergas = schema.organo("sergas");
    OrganoId augas = schema.organo("augas-de-galicia");
    ledger.record(
        sergas,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));
    ledger.record(
        augas,
        new LicitacionOutstandingRecord(ANOTHER_PUBLICATION, null, null, 2, "Adxudicado"));

    List<LicitacionOutstandingRecord> outstanding = ledger.outstandingFor(sergas);

    assertThat(outstanding)
        .extracting(LicitacionOutstandingRecord::publicationId)
        .containsExactly(PUBLICATION);
  }

  // The reason filing an entry takes a transaction of its own: it describes a failure, and the
  // transaction it is filed from is the one that failure is about to roll back.
  @Test
  void entry_filed_from_the_transaction_that_fails_outlives_it() throws SQLException {
    OrganoId organoId = schema.organo("sergas");

    assertThatThrownBy(
            () ->
                caller.recordThenFail(
                    organoId,
                    new LicitacionOutstandingRecord(
                        PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado")))
        .isInstanceOf(IllegalStateException.class);

    assertThat(ledgerTable()).hasNumberOfRows(1);
  }

  // And the reason dropping one does not: an entry stops being outstanding exactly when the
  // procedure it named is stored, so a store that never settles must leave it to be retried.
  @Test
  void entry_dropped_by_the_transaction_that_fails_is_restored_with_it() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    assertThatThrownBy(() -> caller.clearThenFail(organoId, PUBLICATION))
        .isInstanceOf(IllegalStateException.class);

    assertThat(ledgerTable()).hasNumberOfRows(1);
  }

  // The completion gate answers from what has settled, never from the caller's own clearing. An
  // Órgano completed on an uncommitted answer, whose transaction then rolls back, would carry a
  // ledger it can never return to — the incremental mode that would revisit it is unbuilt.
  @Test
  void the_completion_gate_ignores_clearing_the_caller_has_not_settled() throws SQLException {
    OrganoId organoId = schema.organo("sergas");
    ledger.record(
        organoId,
        new LicitacionOutstandingRecord(
            PUBLICATION, PUBLISHED_ON, MODIFIED_ON, 8, "Formalizado"));

    boolean outstanding = caller.clearThenAskWhetherAnythingIsOutstanding(organoId, PUBLICATION);

    assertThat(outstanding).isTrue();
  }

  private Table ledgerTable() {
    return Tables.orderedBy(
        unproxied, "licitacion_outstanding_record", "organo_id", "publication_id");
  }
}
