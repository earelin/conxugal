package gal.conxugal.infrastructure.jdbc.licitacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.licitacion.Licitacion;
import gal.conxugal.domain.licitacion.LicitacionContractType;
import gal.conxugal.domain.licitacion.LicitacionContractTypeRepository;
import gal.conxugal.domain.licitacion.LicitacionProcedureType;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeRepository;
import gal.conxugal.domain.licitacion.LicitacionRepository;
import gal.conxugal.domain.licitacion.LicitacionState;
import gal.conxugal.domain.licitacion.LicitacionStateRepository;
import gal.conxugal.domain.licitacion.LicitacionTramitacionType;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeRepository;
import gal.conxugal.domain.licitacion.PublicationId;
import gal.conxugal.domain.licitacion.UpsertOperation;
import gal.conxugal.domain.licitacion.UpsertOutcome;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.assertj.core.api.SoftAssertions;
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
 * The procedure's adapter against a real PostgreSQL. What a write left behind is asserted over the
 * table rather than read back through the same adapter, except where the read <em>is</em> the
 * method under test — which is most of what matters here, since the four fetch joins are the only
 * thing that makes {@code findByPublicationId} able to return a valid aggregate at all.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcLicitacionRepositoryIntegrationTest implements TestPropertyProvider {

  private static final PublicationId PUBLICATION = new PublicationId("822054");
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);
  private static final LocalDate MODIFIED_ON = LocalDate.of(2026, 5, 2);

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  LicitacionRepository licitacionRepository;

  @Inject
  LicitacionStateRepository stateRepository;

  @Inject
  LicitacionContractTypeRepository contractTypeRepository;

  @Inject
  LicitacionProcedureTypeRepository procedureTypeRepository;

  @Inject
  LicitacionTramitacionTypeRepository tramitacionTypeRepository;

  @Inject
  DataSource dataSource;

  private OrganoId organoId;

  @BeforeEach
  void setUp() throws Exception {
    organoId = new CatalogueFixture(dataSource).organo("consorcio-x");
  }

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void upsert_assigns_the_identity_and_reports_the_procedure_as_added() {
    UpsertOutcome outcome = licitacionRepository.upsert(fullyPublished());

    assertThat(outcome.operation()).isEqualTo(UpsertOperation.ADDED);
    Table procedures = licitacionTable();
    assertThat(procedures).hasNumberOfRows(1);
    assertThat(procedures)
        .row(0)
            .value("id").isEqualTo(outcome.id().value())
            .value("publication_id").isEqualTo("822054")
            .value("organo_id").isEqualTo(organoId.value())
            .value("publication_date").isEqualTo(PUBLISHED_ON)
            .value("last_modified").isEqualTo(MODIFIED_ON)
            .value("expediente").isEqualTo("EXP/2026/0001")
            .value("obxecto").isEqualTo("Servizo de limpeza")
            .value("lote_count").isEqualTo(2)
            .value("base_budget").isEqualTo(new BigDecimal("3052743.72"))
            .value("estimated_value").isEqualTo(new BigDecimal("2523756.79"))
            .value("withdrawn").isFalse();
  }

  // The natural key is what a re-import matches on, so the second reading lands on the row the
  // first created and the identity every child hangs off is unchanged.
  @Test
  void storing_procedure_twice_under_one_publication_identifier_leaves_one_row() {
    UpsertOutcome first = licitacionRepository.upsert(fullyPublished());

    UpsertOutcome again = licitacionRepository.upsert(fullyPublished());

    assertThat(again.id()).isEqualTo(first.id());
    assertThat(again.operation()).isEqualTo(UpsertOperation.REFRESHED);
    assertThat(licitacionTable()).hasNumberOfRows(1);
  }

  @Test
  void re_storing_procedure_refreshes_its_published_values_in_place() {
    UpsertOutcome first = licitacionRepository.upsert(fullyPublished());
    LicitacionState corrected = stateRepository.upsert(new LicitacionState(3, "Formalizado"));

    licitacionRepository.upsert(
        procedure(corrected, null, null, null, "Obxecto corrixido", PUBLISHED_ON));

    assertThat(licitacionTable()).hasNumberOfRows(1);
    assertThat(licitacionTable())
        .row(0)
            .value("id").isEqualTo(first.id().value())
            .value("obxecto").isEqualTo("Obxecto corrixido")
            .value("state_id").isEqualTo(identityOf(corrected))
            .value("contract_type_id").isNull();
  }

  // The proof of the four fetch joins. Without them the mapper builds each reference as an id-only
  // stub, which none of these four entities can be built from, so all four arrive null and the
  // aggregate's constructor refuses the null state — on every stored row.
  @Test
  void find_by_publication_id_reads_back_all_four_vocabulary_references() {
    licitacionRepository.upsert(fullyPublished());

    Licitacion stored = licitacionRepository.findByPublicationId(PUBLICATION).orElseThrow();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(stored.state().code()).isEqualTo(2);
      softly.assertThat(stored.state().label()).isEqualTo("Adxudicado");
      softly.assertThat(stored.contractType()).isNotNull();
      softly.assertThat(stored.contractType().name()).isEqualTo("Servizos");
      softly.assertThat(stored.procedureType()).isNotNull();
      softly.assertThat(stored.procedureType().name()).isEqualTo("Aberto");
      softly.assertThat(stored.tramitacionType()).isNotNull();
      softly.assertThat(stored.tramitacionType().name()).isEqualTo("Ordinaria");
    });
  }

  // The other half of the proof, and the reason the joins have to be left: an inner join would
  // drop this procedure from a read that asked for it by its identifier, and no join at all would
  // lose its state silently.
  @Test
  void find_by_publication_id_reads_back_procedure_that_published_none_of_the_three_types() {
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    licitacionRepository.upsert(
        procedure(state, null, null, null, "Servizo de limpeza", PUBLISHED_ON));

    Licitacion stored = licitacionRepository.findByPublicationId(PUBLICATION).orElseThrow();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(stored.state().code()).isEqualTo(2);
      softly.assertThat(stored.contractType()).isNull();
      softly.assertThat(stored.procedureType()).isNull();
      softly.assertThat(stored.tramitacionType()).isNull();
    });
  }

  @Test
  void find_by_publication_id_reads_back_every_published_value_it_was_stored_with() {
    licitacionRepository.upsert(fullyPublished());

    Licitacion stored = licitacionRepository.findByPublicationId(PUBLICATION).orElseThrow();

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(stored.id()).isNotNull();
      softly.assertThat(stored.publicationId()).isEqualTo(PUBLICATION);
      softly.assertThat(stored.organoId()).isEqualTo(organoId);
      softly.assertThat(stored.publicationDate()).isEqualTo(PUBLISHED_ON);
      softly.assertThat(stored.lastModified()).isEqualTo(MODIFIED_ON);
      softly.assertThat(stored.expediente()).isEqualTo("EXP/2026/0001");
      softly.assertThat(stored.obxecto()).isEqualTo("Servizo de limpeza");
      softly.assertThat(stored.loteCount()).isEqualTo(2);
      softly.assertThat(stored.baseBudget()).isEqualTo(new Money(new BigDecimal("3052743.72")));
      softly.assertThat(stored.estimatedValue()).isEqualTo(new Money(new BigDecimal("2523756.79")));
      softly.assertThat(stored.withdrawn()).isFalse();
    });
  }

  // A date the adapter could not interpret arrives here as null and is not a reason to refuse the
  // procedure, so the read has to survive one.
  @Test
  void procedure_whose_publication_date_could_not_be_interpreted_stores_and_reads_back() {
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    licitacionRepository.upsert(procedure(state, null, null, null, "Servizo de limpeza", null));

    Licitacion stored = licitacionRepository.findByPublicationId(PUBLICATION).orElseThrow();

    assertThat(stored.publicationDate()).isNull();
  }

  // How the source mints its identifiers is the source's business: the column holds what was
  // published rather than a reading of it.
  @Test
  void publication_identifier_that_is_not_numeric_round_trips() {
    PublicationId published = new PublicationId("LIC-2026/0001");
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    licitacionRepository.upsert(
        new Licitacion(
            published, organoId, PUBLISHED_ON, MODIFIED_ON, state, null, null, null, null, null,
            null, null, null));

    Optional<Licitacion> stored = licitacionRepository.findByPublicationId(published);

    assertThat(stored).isPresent();
    assertThat(stored.orElseThrow().publicationId()).isEqualTo(published);
  }

  @Test
  void find_by_publication_id_answers_nothing_for_identifier_nobody_stored() {
    assertThat(licitacionRepository.findByPublicationId(new PublicationId("999999"))).isEmpty();
  }

  // The diagnosis belongs where the mistake is: a null here would reach the database as a null in
  // a NOT NULL foreign key, whose error names the column rather than the missing upsert.
  @Test
  void upsert_refuses_procedure_whose_state_carries_no_identity() {
    Licitacion unstoredState =
        procedure(new LicitacionState(2, "Adxudicado"), null, null, null, "Obxecto", PUBLISHED_ON);

    assertThatThrownBy(() -> licitacionRepository.upsert(unstoredState))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("state must be stored");
    assertThat(licitacionTable()).hasNumberOfRows(0);
  }

  @Test
  void upsert_refuses_procedure_whose_contract_type_carries_no_identity() {
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    Licitacion unstoredType =
        procedure(
            state,
            new LicitacionContractType("Servizos"),
            null,
            null,
            "Obxecto",
            PUBLISHED_ON);

    assertThatThrownBy(() -> licitacionRepository.upsert(unstoredType))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contract type must be stored");
  }

  @Test
  void upsert_refuses_procedure_whose_procedure_type_carries_no_identity() {
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    Licitacion unstoredType =
        procedure(
            state, null, new LicitacionProcedureType("Aberto"), null, "Obxecto", PUBLISHED_ON);

    assertThatThrownBy(() -> licitacionRepository.upsert(unstoredType))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("procedure type must be stored");
  }

  @Test
  void upsert_refuses_procedure_whose_tramitacion_type_carries_no_identity() {
    LicitacionState state = stateRepository.upsert(new LicitacionState(2, "Adxudicado"));
    Licitacion unstoredType =
        procedure(
            state,
            null,
            null,
            new LicitacionTramitacionType("Ordinaria"),
            "Obxecto",
            PUBLISHED_ON);

    assertThatThrownBy(() -> licitacionRepository.upsert(unstoredType))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tramitación type must be stored");
  }

  private Licitacion fullyPublished() {
    return procedure(
        stateRepository.upsert(new LicitacionState(2, "Adxudicado")),
        contractTypeRepository.upsert(new LicitacionContractType("Servizos")),
        procedureTypeRepository.upsert(new LicitacionProcedureType("Aberto")),
        tramitacionTypeRepository.upsert(new LicitacionTramitacionType("Ordinaria")),
        "Servizo de limpeza",
        PUBLISHED_ON);
  }

  private Licitacion procedure(
      LicitacionState state,
      @Nullable LicitacionContractType contractType,
      @Nullable LicitacionProcedureType procedureType,
      @Nullable LicitacionTramitacionType tramitacionType,
      String obxecto,
      @Nullable LocalDate publicationDate) {
    return new Licitacion(
        PUBLICATION,
        organoId,
        publicationDate,
        MODIFIED_ON,
        state,
        "EXP/2026/0001",
        obxecto,
        contractType,
        procedureType,
        tramitacionType,
        2,
        new Money(new BigDecimal("3052743.72")),
        new Money(new BigDecimal("2523756.79")));
  }

  // The identity the vocabulary upsert answered with, which is nullable until the database assigns
  // it: a null reaching the comparison would read as the row simply not matching.
  private static UUID identityOf(LicitacionState state) {
    return Objects.requireNonNull(
            state.id(), "the upsert answers the stored state with its identity")
        .value();
  }

  private Table licitacionTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("licitacion")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("publication_id")})
        .build();
  }
}
