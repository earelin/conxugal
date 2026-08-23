package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Cpv;
import gal.conxugal.domain.licitacion.CpvRepository;
import gal.conxugal.domain.licitacion.LicitacionContractType;
import gal.conxugal.domain.licitacion.LicitacionContractTypeRepository;
import gal.conxugal.domain.licitacion.LicitacionProcedureType;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeRepository;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateRepository;
import gal.conxugal.domain.licitacion.LicitacionTramitacionType;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeRepository;
import gal.conxugal.domain.licitacion.Nut;
import gal.conxugal.domain.licitacion.NutRepository;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The six vocabulary adapters against a real PostgreSQL. What each write left behind is asserted
 * over the table rather than read back through the same adapter — none of these ports offers a
 * finder, and a read-back would only show one adapter agreeing with itself.
 *
 * <p>The property under test throughout is that <strong>an unseen value costs a row</strong>.
 * Nothing seeds any of these tables, so a state code, a type name or a CPV code the source has
 * never published before simply creates its row rather than failing a foreign key and rejecting the
 * procedure that named it.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcVocabularyRepositoryIntegrationTest implements TestPropertyProvider {

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionStateRepository stateRepository;

  @Inject
  LicitacionContractTypeRepository contractTypeRepository;

  @Inject
  LicitacionProcedureTypeRepository procedureTypeRepository;

  @Inject
  LicitacionTramitacionTypeRepository tramitacionTypeRepository;

  @Inject
  CpvRepository cpvRepository;

  @Inject
  NutRepository nutRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void state_upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionState stored = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    assertThat(stored.code()).isEqualTo(2);
    Table states = stateTable();
    assertThat(states).hasNumberOfRows(1);
    assertThat(states)
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("code").isEqualTo(2)
            .value("label").isEqualTo("Adxudicado");
  }

  // Codes 101 and 102 are both published as Histórico. This is the test that would fail if the
  // label ever became the key: the store would hold one row where the source publishes two states.
  @Test
  void two_codes_sharing_one_label_are_two_states() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(101, "Histórico"));
    LicitacionState second = stateRepository.upsert(new LicitacionState(102, "Histórico"));

    assertThat(first.id()).isNotEqualTo(second.id());
    Table states = stateTable();
    assertThat(states).hasNumberOfRows(2);
    assertThat(states)
        .row(0)
            .value("code").isEqualTo(101)
            .value("label").isEqualTo("Histórico");
    assertThat(states)
        .row(1)
            .value("code").isEqualTo(102)
            .value("label").isEqualTo("Histórico");
  }

  // Code 7 was never observed and the set is not closed, so an unknown code costs a row rather
  // than a foreign-key violation and a rejected procedure.
  @Test
  void state_code_the_table_has_never_held_stores_rather_than_failing() {
    LicitacionState stored = stateRepository.upsert(new LicitacionState(7, null));

    assertThat(stored.id()).isNotNull();
    assertThat(stateTable()).hasNumberOfRows(1);
    assertThat(stateTable())
        .row(0)
            .value("code").isEqualTo(7)
            .value("label").isNull();
  }

  // A run over thousands of procedures naming one state must not grow the vocabulary.
  @Test
  void re_storing_state_the_source_has_published_before_leaves_one_row() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    LicitacionState again = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(stateTable()).hasNumberOfRows(1);
  }

  // The label is the one thing about a state the source can correct, and the code is what the row
  // is matched on, so the correction lands on the row already stored.
  @Test
  void re_storing_state_under_corrected_label_refreshes_it_in_place() {
    LicitacionState first = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));

    stateRepository.upsert(new LicitacionState(2, "Adxudicado definitivamente"));

    assertThat(stateTable()).hasNumberOfRows(1);
    assertThat(stateTable())
        .row(0)
            .value("id").isEqualTo(identityOf(first))
            .value("label").isEqualTo("Adxudicado definitivamente");
  }

  @Test
  void contract_type_upsert_assigns_the_identity_the_procedure_refers_to() {
    LicitacionContractType stored =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    assertThat(contractTypeTable()).hasNumberOfRows(1);
    assertThat(contractTypeTable())
        .row(0)
            .value("id").isEqualTo(identityOf(stored))
            .value("name").isEqualTo("Servizos");
  }

  @Test
  void type_name_nobody_has_published_before_stores_rather_than_failing() {
    LicitacionContractType stored =
        contractTypeRepository.upsert(new LicitacionContractType("Concesión de obras públicas"));

    assertThat(stored.id()).isNotNull();
    assertThat(contractTypeTable()).hasNumberOfRows(1);
  }

  @Test
  void re_storing_type_the_source_has_published_before_leaves_one_row() {
    LicitacionContractType first =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    LicitacionContractType again =
        contractTypeRepository.upsert(new LicitacionContractType("Servizos"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(contractTypeTable()).hasNumberOfRows(1);
  }

  // The three type vocabularies are structurally identical and separately keyed, so one published
  // name meaning three different things is three entries in three tables.
  @Test
  void one_name_published_as_three_kinds_of_type_is_three_entries() {
    contractTypeRepository.upsert(new LicitacionContractType("Aberto"));
    procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));
    tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Aberto"));

    assertThat(contractTypeTable()).hasNumberOfRows(1);
    assertThat(table("licitacion_procedure_type", "name")).hasNumberOfRows(1);
    assertThat(table("licitacion_tramitacion_type", "name")).hasNumberOfRows(1);
  }

  // The criterion is one row in each of the four, and the three type adapters are byte-identical
  // to one another — which is exactly why a copy that named the wrong table or the wrong constraint
  // would go unnoticed unless each one is driven into its own conflict branch.
  @Test
  void re_storing_procedure_type_the_source_has_published_before_leaves_one_row() {
    LicitacionProcedureType first =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    LicitacionProcedureType again =
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(table("licitacion_procedure_type", "name")).hasNumberOfRows(1);
  }

  @Test
  void re_storing_tramitacion_type_the_source_has_published_before_leaves_one_row() {
    LicitacionTramitacionType first =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria"));

    LicitacionTramitacionType again =
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(table("licitacion_tramitacion_type", "name")).hasNumberOfRows(1);
  }

  // CPV is versioned rather than closed — the 2008 revision retired codes the 2003 one issued —
  // and this system imports procedures published across both, so nothing seeds the list.
  @Test
  void cpv_code_the_list_has_never_held_stores_with_no_description() {
    Cpv stored = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(stored.id()).isNotNull();
    assertThat(cpvTable()).hasNumberOfRows(1);
    assertThat(cpvTable())
        .row(0)
            .value("code").isEqualTo("45000000")
            .value("description").isNull();
  }

  // The record's CPV table publishes the code alone, so every import carries no wording at all. An
  // upsert that wrote it would empty a description something else had supplied, on the next run
  // over any procedure citing that code.
  @Test
  void cpv_upsert_leaves_the_stored_description_alone() {
    cpvRepository.upsert(new Cpv(null, "45000000", "Traballos de construción"));

    Cpv again = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(again.description()).isEqualTo("Traballos de construción");
    assertThat(cpvTable()).hasNumberOfRows(1);
    assertThat(cpvTable())
        .row(0)
            .value("description").isEqualTo("Traballos de construción");
  }

  @Test
  void nut_upsert_leaves_the_stored_description_alone() {
    nutRepository.upsert(new Nut(null, "ES111", "A Coruña"));

    Nut again = nutRepository.upsert(new Nut("ES111"));

    assertThat(again.description()).isEqualTo("A Coruña");
    assertThat(nutTable()).hasNumberOfRows(1);
    assertThat(nutTable())
        .row(0)
            .value("code").isEqualTo("ES111")
            .value("description").isEqualTo("A Coruña");
  }

  @Test
  void re_storing_entry_the_list_already_holds_leaves_one_row() {
    Cpv first = cpvRepository.upsert(new Cpv("45000000"));

    Cpv again = cpvRepository.upsert(new Cpv("45000000"));

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(cpvTable()).hasNumberOfRows(1);
  }

  // The identity the upsert answered with. Every one of these types is nullable until the database
  // assigns it, and a null reaching the comparison would read as the row simply not matching, so it
  // is required here rather than left to fail as a mismatch.
  private static UUID identityOf(LicitacionState state) {
    return Objects.requireNonNull(
            state.id(), "the upsert answers the stored state with its identity")
        .value();
  }

  private static UUID identityOf(LicitacionContractType type) {
    return Objects.requireNonNull(type.id(), "the upsert answers the stored type with its identity")
        .value();
  }

  private Table stateTable() {
    return table("licitacion_state", "code");
  }

  private Table contractTypeTable() {
    return table("licitacion_contract_type", "name");
  }

  private Table cpvTable() {
    return table("cpv", "code");
  }

  private Table nutTable() {
    return table("nut", "code");
  }

  // Ordered on the published key so row(n) is stable, rather than leaning on uuidv7 insertion
  // order.
  private Table table(String name, String key) {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table(name)
        .columnsToOrder(new Table.Order[] {Table.Order.asc(key)})
        .build();
  }
}
