package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
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
 * What the two import-state tables hold, what they refuse, and — as loudly as the rest — what they
 * deliberately do not hold. Most of these claims are about a refusal or an absence, and no
 * repository call can show either.
 *
 * <p>Driven with raw SQL and committed: a deliberately violated constraint aborts the connection
 * Micronaut Data shares with the test.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionImportStateMigrationIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  DataSource dataSource;

  private SchemaFixture schema;

  @BeforeEach
  void setUp() {
    schema = SchemaFixture.committing(dataSource);
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  // The status and the offset, and nothing measuring an instant this history is covered through:
  // that column exists on the other family's state because its incremental window is measured from
  // it, and this family's incremental mode is driven by the listing's ordering instead.
  @Test
  void the_state_holds_the_status_and_the_offset_alone() throws SQLException {
    assertThat(schema.columnNamesOf("licitacion_import_state"))
        .containsExactlyInAnyOrder("organo_id", "state", "cursor_offset");
  }

  // The four listing-sourced values beside the identifier, and no count of attempts, no delay and
  // no time a next attempt is due: this is a set of identifiers to try once more, not a queue.
  @Test
  void the_ledger_holds_the_four_listing_values_beside_the_identifier() throws SQLException {
    assertThat(schema.columnNamesOf("licitacion_outstanding_record"))
        .containsExactlyInAnyOrder(
            "organo_id",
            "publication_id",
            "publication_date",
            "last_modified",
            "state_code",
            "state_label");
  }

  // Matching licitacion.publication_id: the ledger holds identifiers to come back to, and one it
  // could not represent would be one the walk could never retry.
  @Test
  void the_ledger_holds_the_publication_identifier_as_text() throws SQLException {
    assertThat(schema.columnTypeOf("licitacion_outstanding_record", "publication_id"))
        .isEqualTo("text");
  }

  @Test
  void no_foreign_key_of_the_state_reaches_anything_but_the_organo_catalogue()
      throws SQLException {
    assertThat(schema.foreignKeyTargetsOf("licitacion_import_state"))
        .containsExactly("organo_contratacion");
  }

  // The two state columns are the published values, not a reference: the vocabulary row an entry
  // will need may not exist yet, and pointing at one would make the ledger depend on a write it
  // does not perform.
  @Test
  void the_ledger_reaches_the_organo_catalogue_and_no_vocabulary() throws SQLException {
    assertThat(schema.foreignKeyTargetsOf("licitacion_outstanding_record"))
        .containsExactly("organo_contratacion");
  }

  // One row per Órgano or none, which is what makes an Órgano with no row mean never started.
  @Test
  void second_state_row_for_the_same_organo_is_refused() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");
    schema.insertLicitacionImportState(organoId, "INCOMPLETE", null);

    assertThatThrownBy(() -> schema.insertLicitacionImportState(organoId, "COMPLETE", 400))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesUniqueness(exception, "licitacion_import_state_pkey"));
  }

  @Test
  void state_of_an_unknown_organo_is_refused() throws SQLException {
    assertThatThrownBy(
            () -> schema.insertLicitacionImportState(UUID.randomUUID(), "INCOMPLETE", null))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesForeignKey(
                    exception, "licitacion_import_state_organo_id_fkey"));
  }

  @Test
  void state_row_stating_no_status_is_refused() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");

    assertThatThrownBy(() -> schema.insertLicitacionImportStateWithoutState(organoId))
        .isInstanceOfSatisfying(SQLException.class, Refusals::violatesNotNull);
  }

  // The walk meets the same failing record on every run, so one entry per publication and Órgano
  // is what stops the ledger accumulating.
  @Test
  void second_ledger_entry_for_the_same_publication_is_refused() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");
    schema.insertOutstandingRecord(organoId, "822054", null, null, 8, "Formalizado");

    assertThatThrownBy(
            () -> schema.insertOutstandingRecord(organoId, "822054", null, null, 2, "Adxudicado"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesUniqueness(exception, "licitacion_outstanding_record_pkey"));
  }

  // The same publication outstanding for two Órganos is two entries: the key is the pair, and the
  // source serves both contract families from one identifier space.
  @Test
  void the_same_publication_stays_outstanding_for_another_organo() throws SQLException {
    UUID sergas = schema.insertOrgano("sergas");
    UUID augas = schema.insertOrgano("augas-de-galicia");
    schema.insertOutstandingRecord(sergas, "822054", null, null, 8, "Formalizado");
    schema.insertOutstandingRecord(augas, "822054", null, null, 8, "Formalizado");

    assertThat(ledger()).hasNumberOfRows(2);
  }

  @Test
  void ledger_entry_of_an_unknown_organo_is_refused() {
    assertThatThrownBy(
            () ->
                schema.insertOutstandingRecord(
                    UUID.randomUUID(), "822054", null, null, 8, "Formalizado"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesForeignKey(
                    exception, "licitacion_outstanding_record_organo_id_fkey"));
  }

  @Test
  void ledger_entry_stating_no_state_code_is_refused() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");

    assertThatThrownBy(() -> schema.insertOutstandingRecordWithoutStateCode(organoId, "822054"))
        .isInstanceOfSatisfying(SQLException.class, Refusals::violatesNotNull);
  }

  // A date the source published in a form nothing could interpret is absent, and the label is not
  // a key, so an entry may carry neither.
  @Test
  void ledger_entry_may_hold_neither_date_nor_label() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");

    schema.insertOutstandingRecord(organoId, "822054", null, null, 8, null);

    assertThat(ledger())
        .row(0)
        .value("publication_date")
        .isNull()
        .value("last_modified")
        .isNull()
        .value("state_label")
        .isNull();
  }

  // A started import has consumed no page yet, so the offset is written only once one commits.
  @Test
  void started_import_may_hold_no_offset_yet() throws SQLException {
    UUID organoId = schema.insertOrgano("sergas");

    schema.insertLicitacionImportState(organoId, "INCOMPLETE", null);

    assertThat(importState()).row(0).value("cursor_offset").isNull();
  }

  // Nothing here touches the other family's state: its table keeps every column it shipped with.
  @Test
  void the_other_contract_family_keeps_the_state_columns_it_shipped_with() throws SQLException {
    assertThat(schema.columnNamesOf("contrato_menor_import_state"))
        .containsExactlyInAnyOrder(
            "organo_id", "state", "cursor_date", "covered_through", "refreshed_through");
  }

  private Table ledger() {
    return Tables.orderedBy(
        dataSource, "licitacion_outstanding_record", "organo_id", "publication_id");
  }

  private Table importState() {
    return Tables.orderedBy(dataSource, "licitacion_import_state", "organo_id");
  }
}
