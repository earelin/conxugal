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
import java.util.List;
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
 * V19's guarantees for the procedure and the six vocabularies it refers to, verified directly
 * rather than through an adapter: most of the claims are about what a table refuses and what it
 * deliberately does <em>not</em> hold, and no repository call can show either. The duplicate cases
 * in particular have to bypass the upserts, whose whole job is to absorb a repeated key.
 *
 * <p>Driven with raw SQL and committed, unlike the adapters' own tests: a deliberately violated
 * constraint aborts the connection Micronaut Data shares with them.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LicitacionMigrationIntegrationTest implements TestPropertyProvider {

  private static final List<String> VOCABULARY_TABLES =
      List.of(
          "licitacion_state",
          "licitacion_contract_type",
          "licitacion_procedure_type",
          "licitacion_tramitacion_type",
          "cpv",
          "nut");

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

  // The state and the three types are references and nothing else: a label or a type name here
  // would be the normalised schema quietly denormalised, and a procedure holding the label could
  // not tell code 101 from code 102.
  @Test
  void the_procedure_holds_its_published_values_and_four_vocabulary_references() throws Exception {
    assertThat(schema.columnNamesOf("licitacion"))
        .containsExactlyInAnyOrder(
            "id",
            "publication_id",
            "organo_id",
            "publication_date",
            "last_modified",
            "state_id",
            "expediente",
            "obxecto",
            "contract_type_id",
            "procedure_type_id",
            "tramitacion_type_id",
            "lote_count",
            "base_budget",
            "estimated_value",
            "withdrawn");
  }

  @Test
  void the_state_holds_its_code_and_the_label_the_source_repeats() throws Exception {
    assertThat(schema.columnNamesOf("licitacion_state"))
        .containsExactlyInAnyOrder("id", "code", "label");
  }

  // No source identifier column beside the name: the record's <dd> publishes no id for a type and
  // the listing endpoint has no type field at all, so the published name is the whole key.
  @Test
  void each_type_vocabulary_holds_its_published_name_alone() throws Exception {
    assertThat(schema.columnNamesOf("licitacion_contract_type"))
        .containsExactlyInAnyOrder("id", "name");
    assertThat(schema.columnNamesOf("licitacion_procedure_type"))
        .containsExactlyInAnyOrder("id", "name");
    assertThat(schema.columnNamesOf("licitacion_tramitacion_type"))
        .containsExactlyInAnyOrder("id", "name");
  }

  @Test
  void the_regulated_lists_hold_the_code_and_somewhere_for_the_wording() throws Exception {
    assertThat(schema.columnNamesOf("cpv")).containsExactlyInAnyOrder("id", "code", "description");
    assertThat(schema.columnNamesOf("nut")).containsExactlyInAnyOrder("id", "code", "description");
  }

  // R13's withdrawal has nothing to say about a published value: these are not parts of a procedure
  // the source restates, and a state no procedure references any more is still one it published.
  @Test
  void no_vocabulary_table_carries_the_withdrawal_marker() throws Exception {
    for (String table : VOCABULARY_TABLES) {
      assertThat(schema.columnNamesOf(table)).doesNotContain("withdrawn");
    }
  }

  @Test
  void the_procedure_is_born_visible() throws Exception {
    storedProcedure("822054");

    assertThat(table("licitacion", "publication_id")).hasNumberOfRows(1);
    assertThat(table("licitacion", "publication_id"))
        .row(0)
            .value("withdrawn").isFalse();
  }

  @Test
  void unique_publication_id_refuses_second_procedure_under_stored_identifier() throws Exception {
    UUID organoId = schema.insertOrgano("consorcio-x");
    UUID stateId = schema.insertState(2, "Adxudicado");
    schema.insertLicitacion("822054", organoId, stateId);

    assertThatThrownBy(() -> schema.insertLicitacion("822054", organoId, stateId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_publication_id_key"));
  }

  // The two families are addressed from one identifier space, so the same publication under
  // another Órgano is the same publication and not a second one.
  @Test
  void the_same_publication_id_under_another_organo_is_still_duplicate() throws Exception {
    UUID stateId = schema.insertState(2, "Adxudicado");
    UUID first = schema.insertOrgano("consorcio-x");
    UUID second = schema.insertOrgano("axencia-y");
    schema.insertLicitacion("822054", first, stateId);

    assertThatThrownBy(() -> schema.insertLicitacion("822054", second, stateId))
        .isInstanceOfSatisfying(
            SQLException.class,
            Refusals::violatesUniqueness);
  }

  // An identifier the source did not mint as a number stores unchanged: how it mints them is the
  // source's business, and the column is matched on rather than ordered or summed.
  @Test
  void publication_identifier_that_is_not_numeric_stores_unchanged() throws Exception {
    UUID organoId = schema.insertOrgano("consorcio-x");
    UUID stateId = schema.insertState(2, "Adxudicado");

    schema.insertLicitacion("LIC-2026/0001", organoId, stateId);

    assertThat(table("licitacion", "publication_id")).hasNumberOfRows(1);
    assertThat(table("licitacion", "publication_id"))
        .row(0)
            .value("publication_id").isEqualTo("LIC-2026/0001");
  }

  @Test
  void unique_state_code_refuses_second_state_under_stored_code() throws Exception {
    schema.insertState(101, "Histórico");

    assertThatThrownBy(() -> schema.insertState(101, "Outra cousa"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "licitacion_state_code_key"));
  }

  // 101 and 102 are both published as Histórico. This is the test that would fail if the label
  // ever gained a UNIQUE: the store would reject the second real state the source publishes.
  @Test
  void two_states_sharing_one_label_are_two_states() throws Exception {
    schema.insertState(101, "Histórico");
    schema.insertState(102, "Histórico");

    Table states = table("licitacion_state", "code");
    assertThat(states).hasNumberOfRows(2);
    assertThat(states).row(0).value("code").isEqualTo(101);
    assertThat(states).row(1).value("code").isEqualTo(102);
  }

  @Test
  void unique_type_name_refuses_second_type_under_stored_name() throws Exception {
    schema.insertContractType("Servizos");

    assertThatThrownBy(() -> schema.insertContractType("Servizos"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception ->
                Refusals.violatesUniqueness(exception, "licitacion_contract_type_name_key"));
  }

  // Structurally identical vocabularies, separately keyed: one name published for two different
  // kinds of type is two entries, in two tables.
  @Test
  void one_name_published_as_two_kinds_of_type_is_two_entries() throws Exception {
    schema.insertContractType("Aberto");
    schema.insertProcedureType("Aberto");

    assertThat(table("licitacion_contract_type", "name")).hasNumberOfRows(1);
    assertThat(table("licitacion_procedure_type", "name")).hasNumberOfRows(1);
  }

  @Test
  void unique_cpv_code_refuses_second_entry_under_stored_code() throws Exception {
    schema.insertCpv("45000000", "Traballos de construción");

    assertThatThrownBy(() -> schema.insertCpv("45000000", "Outra redacción"))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesUniqueness(exception, "cpv_code_key"));
  }

  // The wording is translated, revised and shared across sibling entries, so a UNIQUE on it would
  // reject a real entry of a regulated list.
  @Test
  void two_entries_sharing_one_description_stay_two_entries() throws Exception {
    schema.insertCpv("45000000", "Traballos de construción");
    schema.insertCpv("45100000", "Traballos de construción");
    schema.insertNut("ES111", "Galicia");
    schema.insertNut("ES112", "Galicia");

    assertThat(table("cpv", "code")).hasNumberOfRows(2);
    assertThat(table("nut", "code")).hasNumberOfRows(2);
  }

  @Test
  void procedure_of_unknown_organo_is_refused_by_the_foreign_key() throws Exception {
    UUID stateId = schema.insertState(2, "Adxudicado");

    assertThatThrownBy(() -> schema.insertLicitacion("822054", UUID.randomUUID(), stateId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesForeignKey(exception, "licitacion_organo_id_fkey"));
  }

  @Test
  void procedure_naming_unknown_state_is_refused_by_the_foreign_key() throws Exception {
    UUID organoId = schema.insertOrgano("consorcio-x");

    assertThatThrownBy(() -> schema.insertLicitacion("822054", organoId, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesForeignKey(exception, "licitacion_state_id_fkey"));
  }

  // The listing always publishes a state, so a procedure carrying none is a parse defect rather
  // than a procedure the source published that way.
  @Test
  void procedure_carrying_no_state_at_all_is_refused() throws Exception {
    UUID organoId = schema.insertOrgano("consorcio-x");

    assertThatThrownBy(() -> schema.insertLicitacionWithoutState("822054", organoId))
        .isInstanceOfSatisfying(
            SQLException.class,
            Refusals::violatesNotNull);
  }

  // Plain, with no ON DELETE CASCADE: no import deletes a published value, so a cascade would
  // stand in for a path nothing has.
  @Test
  void deleting_state_that_procedure_names_is_refused() throws Exception {
    UUID organoId = schema.insertOrgano("consorcio-x");
    UUID stateId = schema.insertState(2, "Adxudicado");
    schema.insertLicitacion("822054", organoId, stateId);

    assertThatThrownBy(() -> schema.deleteState(stateId))
        .isInstanceOfSatisfying(
            SQLException.class,
            exception -> Refusals.violatesForeignKey(exception, "licitacion_state_id_fkey"));
    assertThat(table("licitacion", "publication_id")).hasNumberOfRows(1);
  }

  @Test
  void the_procedure_reaches_the_organo_catalogue_and_its_four_vocabularies() throws Exception {
    assertThat(schema.foreignKeyTargetsOf("licitacion"))
        .containsExactlyInAnyOrder(
            "organo_contratacion",
            "licitacion_state",
            "licitacion_contract_type",
            "licitacion_procedure_type",
            "licitacion_tramitacion_type");
  }

  // The natural key and the foreign keys, and nothing else. In particular no
  // (organo_id, publication_date): V13 created exactly that index for contrato_menor and V16
  // dropped it again, so the browsing feature measures its own reads and adds what they ask for.
  @Test
  void the_procedure_is_indexed_on_its_natural_key_and_its_foreign_keys_and_nothing_else()
      throws Exception {
    assertThat(schema.indexNamesOf("licitacion"))
        .containsExactlyInAnyOrder(
            "licitacion_pkey",
            "licitacion_publication_id_key",
            "licitacion_organo_id_idx",
            "licitacion_state_id_idx",
            "licitacion_contract_type_id_idx",
            "licitacion_procedure_type_id_idx",
            "licitacion_tramitacion_type_id_idx");
  }

  // Ordered on the published key so row(n) is stable, rather than leaning on uuidv7 insertion
  // order.
  private Table table(String name, String key) {
    return Tables.orderedBy(dataSource, name, key);
  }

  private UUID storedProcedure(String publicationId) throws SQLException {
    UUID organoId = schema.insertOrgano("consorcio-x");
    UUID stateId = schema.insertState(2, "Adxudicado");
    return schema.insertLicitacion(publicationId, organoId, stateId);
  }
}
