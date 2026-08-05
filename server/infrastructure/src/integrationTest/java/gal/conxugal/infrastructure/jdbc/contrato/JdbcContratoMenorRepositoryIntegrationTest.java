package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorId;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.contrato.UpsertCounts;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * The adapter against a real PostgreSQL. What a batch left behind is asserted over the table
 * rather than read back through the same adapter, so a statement that touched rows it should not
 * have cannot pass; the round-trip tests are the exception, because there the mapping in both
 * directions is the thing under test.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcContratoMenorRepositoryIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

  @Override
  public @NonNull Map<String, String> getProperties() {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    return Map.of(
        "datasources.default.url", postgres.getJdbcUrl(),
        "datasources.default.username", postgres.getUsername(),
        "datasources.default.password", postgres.getPassword(),
        "datasources.default.driverClassName", postgres.getDriverClassName(),
        "datasources.default.dialect", "POSTGRES",
        "flyway.datasources.default.enabled", "true"
    );
  }

  @Inject
  ContratoMenorRepository contratoMenorRepository;

  @Inject
  ContratoMenorTestRepository testRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void stores_batch_of_new_contracts_and_reports_them_all_added() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    UpsertCounts counts = contratoMenorRepository.upsertAll(
        List.of(
            contrato(4711L, organoId, "Subministración de material"),
            contrato(4712L, organoId, "Servizo de limpeza")));

    assertThat(counts).isEqualTo(new UpsertCounts(2, 0));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos)
        .row(0)
            .value("source_id").isEqualTo(4711L)
            .value("organo_id").isEqualTo(organoId.value())
            .value("publication_date").isEqualTo(PUBLISHED_ON)
            .value("obxecto").isEqualTo("Subministración de material")
            .value("amount").isEqualTo(new BigDecimal("1234.50"))
            .value("duration").isEqualTo("1 mes")
            .value("operador_economico_id").isNull();
    assertThat(contratos).row(1).value("source_id").isEqualTo(4712L);
  }

  // Every source-derived column is refreshed, the awarding Órgano included: a contract
  // re-published under another Órgano that kept its old organo_id would leave countByOrganoId
  // disagreeing with the source for both of them, and that count is what tells the walk the
  // history is fully loaded. Same id throughout, so the row moved rather than being re-inserted.
  @Test
  void upserting_stored_source_id_refreshes_that_row_in_place_and_adds_no_second()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OrganoId reassignedTo = insertOrgano("axencia-y");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "As published")));
    UUID idBefore = storedId(4711L);

    UpsertCounts counts = contratoMenorRepository.upsertAll(
        List.of(
            new ContratoMenor(
                4711L,
                reassignedTo,
                LocalDate.of(2026, 4, 1),
                "Corrected at the source",
                new Money(new BigDecimal("999.99")),
                "2 meses",
                null)));

    assertThat(counts).isEqualTo(new UpsertCounts(0, 1));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos)
        .row(0)
            .value("id").isEqualTo(idBefore)
            .value("organo_id").isEqualTo(reassignedTo.value())
            .value("publication_date").isEqualTo(LocalDate.of(2026, 4, 1))
            .value("obxecto").isEqualTo("Corrected at the source")
            .value("amount").isEqualTo(new BigDecimal("999.99"))
            .value("duration").isEqualTo("2 meses");
    assertThat(contratoMenorRepository.countByOrganoId(organoId)).isZero();
    assertThat(contratoMenorRepository.countByOrganoId(reassignedTo)).isEqualTo(1L);
  }

  // The source blanking a field it previously published is a change like any other: EXCLUDED
  // carries the null through, so the stored value is cleared rather than kept.
  @Test
  void refreshing_clears_values_the_source_stopped_publishing() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "As published")));

    contratoMenorRepository.upsertAll(
        List.of(new ContratoMenor(4711L, organoId, null, null, null, null, null)));

    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos)
        .row(0)
            .value("publication_date").isNull()
            .value("obxecto").isNull()
            .value("amount").isNull()
            .value("duration").isNull();
  }

  @Test
  void upserting_the_same_batch_twice_changes_nothing_and_reports_all_refreshed()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    List<ContratoMenor> batch =
        List.of(
            contrato(4711L, organoId, "Subministración de material"),
            contrato(4712L, organoId, "Servizo de limpeza"));
    contratoMenorRepository.upsertAll(batch);
    UUID firstId = storedId(4711L);
    UUID secondId = storedId(4712L);

    UpsertCounts counts = contratoMenorRepository.upsertAll(batch);

    assertThat(counts).isEqualTo(new UpsertCounts(0, 2));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos)
        .row(0)
            .value("id").isEqualTo(firstId)
            .value("source_id").isEqualTo(4711L)
            .value("obxecto").isEqualTo("Subministración de material")
            .value("amount").isEqualTo(new BigDecimal("1234.50"))
            .value("duration").isEqualTo("1 mes");
    assertThat(contratos)
        .row(1)
            .value("id").isEqualTo(secondId)
            .value("source_id").isEqualTo(4712L)
            .value("obxecto").isEqualTo("Servizo de limpeza");
  }

  // This is the number the run outcome states, so an off-by-one here is a wrong report rather
  // than a cosmetic slip.
  @Test
  void reports_both_counts_for_batch_mixing_new_and_already_stored_publications()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(
        List.of(contrato(4711L, organoId, "First"), contrato(4712L, organoId, "Second")));
    UUID untouchedId = storedId(4711L);
    UUID refreshedId = storedId(4712L);

    UpsertCounts counts = contratoMenorRepository.upsertAll(
        List.of(
            contrato(4712L, organoId, "Second, re-read"),
            contrato(4713L, organoId, "Third"),
            contrato(4714L, organoId, "Fourth")));

    assertThat(counts).isEqualTo(new UpsertCounts(2, 1));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(4);
    // The refreshed row kept its identity rather than being deleted and re-inserted, and the
    // one absent from the second batch was left alone entirely.
    assertThat(contratos)
        .row(0)
            .value("source_id").isEqualTo(4711L)
            .value("id").isEqualTo(untouchedId)
            .value("obxecto").isEqualTo("First");
    assertThat(contratos)
        .row(1)
            .value("source_id").isEqualTo(4712L)
            .value("id").isEqualTo(refreshedId)
            .value("obxecto").isEqualTo("Second, re-read");
    assertThat(contratos).row(2).value("source_id").isEqualTo(4713L);
    assertThat(contratos).row(3).value("source_id").isEqualTo(4714L);
  }

  // Absence at the source means nothing, so nothing here removes what an earlier batch stored.
  @Test
  void keeps_contract_absent_from_later_batch_unchanged() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "Still published")));
    UUID retainedId = storedId(4711L);

    contratoMenorRepository.upsertAll(List.of(contrato(4712L, organoId, "A later page")));

    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos)
        .row(0)
            .value("id").isEqualTo(retainedId)
            .value("source_id").isEqualTo(4711L)
            .value("obxecto").isEqualTo("Still published")
            .value("amount").isEqualTo(new BigDecimal("1234.50"));
  }

  @Test
  void an_empty_batch_stores_nothing_and_leaves_what_is_stored_alone() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "Already stored")));

    UpsertCounts counts = contratoMenorRepository.upsertAll(List.of());

    assertThat(counts).isEqualTo(new UpsertCounts(0, 0));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Already stored");
  }

  // PostgreSQL refuses an ON CONFLICT DO UPDATE that would touch one row twice, and that
  // refusal is deterministic — a page repeating a publication would fail identically on every
  // retry and block the Órgano's history for good. The last reading wins, as it does across
  // batches, and the row is counted once because one row is what was written.
  @Test
  void batch_repeating_one_source_id_stores_the_last_reading_once() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    UpsertCounts counts = contratoMenorRepository.upsertAll(
        List.of(
            contrato(4711L, organoId, "First reading"),
            contrato(4712L, organoId, "Another publication"),
            contrato(4711L, organoId, "Same publication, read again")));

    assertThat(counts).isEqualTo(new UpsertCounts(2, 0));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(2);
    assertThat(contratos)
        .row(0)
            .value("source_id").isEqualTo(4711L)
            .value("obxecto").isEqualTo("Same publication, read again");
    assertThat(contratos).row(1).value("source_id").isEqualTo(4712L);
  }

  @Test
  void batch_repeating_stored_source_id_refreshes_it_once() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "As published")));

    UpsertCounts counts = contratoMenorRepository.upsertAll(
        List.of(
            contrato(4711L, organoId, "Read again"),
            contrato(4711L, organoId, "Read once more")));

    assertThat(counts).isEqualTo(new UpsertCounts(0, 1));
    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos).row(0).value("obxecto").isEqualTo("Read once more");
  }

  // Storing null for an operador that has no identity yet would record the award as having no
  // awardee at all, which is what an unusable fiscal identifier means — and it would do it
  // silently. The caller's mistake is refused instead.
  @Test
  void refuses_contract_awarded_to_an_operador_that_has_not_been_stored() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    ContratoMenor awardedToUnsaved =
        new ContratoMenor(
            4711L,
            organoId,
            PUBLISHED_ON,
            "Adxudicado",
            null,
            null,
            new OperadorEconomico("B12345678", "Servizos Galegos SL", new NomeRank(null, 4711L)));

    assertThatThrownBy(() -> contratoMenorRepository.upsertAll(List.of(awardedToUnsaved)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("B12345678");
  }

  @Test
  void counts_the_contracts_of_one_organo_and_ignores_another_organos() throws Exception {
    OrganoId counted = insertOrgano("consorcio-x");
    OrganoId other = insertOrgano("axencia-y");
    contratoMenorRepository.upsertAll(
        List.of(
            contrato(4711L, counted, "First"),
            contrato(4712L, counted, "Second"),
            contrato(4713L, other, "Another Órgano's")));

    assertThat(contratoMenorRepository.countByOrganoId(counted)).isEqualTo(2L);
    assertThat(contratoMenorRepository.countByOrganoId(other)).isEqualTo(1L);
  }

  @Test
  void counts_no_contracts_for_an_organo_that_has_none() throws Exception {
    OrganoId empty = insertOrgano("consorcio-x");

    assertThat(contratoMenorRepository.countByOrganoId(empty)).isZero();
  }

  // Typed aggregate identifiers rest on Micronaut Data populating an id that travels through an
  // AttributeConverter, which its documentation does not demonstrate. Everything above relies on
  // it, so it is asserted rather than assumed. The id is checked against the table rather than
  // against the row findById was asked for, which would hold by construction.
  @Test
  void save_assigns_generated_identity_that_reads_back_as_the_same_value() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    ContratoMenor saved = testRepository.save(contrato(4711L, organoId, "Subministración"));

    ContratoMenorId savedId = saved.id();
    assertThat(savedId).isNotNull();
    assertThat(contratoTable())
        .row(0)
            .value("id").isEqualTo(savedId.value())
            .value("source_id").isEqualTo(4711L);
    assertThat(testRepository.findById(savedId).orElseThrow().obxecto())
        .isEqualTo("Subministración");
  }

  @Test
  void published_text_date_and_amount_round_trip_through_the_store_unchanged() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    String obxecto = "Subministración de material de oficina ".repeat(200);
    String duration = "d".repeat(64);
    contratoMenorRepository.upsertAll(
        List.of(
            new ContratoMenor(
                4711L,
                organoId,
                PUBLISHED_ON,
                obxecto,
                new Money(new BigDecimal("1234.50")),
                duration,
                null)));

    ContratoMenor stored = testRepository.findBySourceId(4711L).orElseThrow();

    assertThat(stored.obxecto()).isEqualTo(obxecto);
    assertThat(stored.duration()).isEqualTo(duration);
    assertThat(stored.publicationDate()).isEqualTo(PUBLISHED_ON);
    // Money's equality is scale-sensitive, so this fails if the column rounds or rescales.
    assertThat(stored.amount()).isEqualTo(new Money(new BigDecimal("1234.50")));
  }

  @Test
  void contract_the_source_left_blank_round_trips_with_no_date_and_no_amount()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    contratoMenorRepository.upsertAll(
        List.of(new ContratoMenor(4711L, organoId, null, null, null, null, null)));

    ContratoMenor stored = testRepository.findBySourceId(4711L).orElseThrow();

    assertThat(stored.publicationDate()).isNull();
    assertThat(stored.amount()).isNull();
    assertThat(stored.obxecto()).isNull();
    assertThat(stored.duration()).isNull();
  }

  @Test
  void contract_with_no_awardee_is_stored_and_read_like_any_other() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");

    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "No usable identifier")));

    ContratoMenor stored = testRepository.findBySourceId(4711L).orElseThrow();
    assertThat(stored.operadorEconomico()).isNull();
    assertThat(stored.obxecto()).isEqualTo("No usable identifier");
  }

  // The awardee's name and fiscal identifier are on operador_economico, so reading them off a
  // contract is a join and nothing else — the contract row holds one identifier.
  @Test
  void the_awardee_is_read_by_joining_operador_economico() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorId operadorId = insertOperador("B12345678", "Servizos Galegos SL");
    contratoMenorRepository.upsertAll(
        List.of(
            new ContratoMenor(
                4711L,
                organoId,
                PUBLISHED_ON,
                "Adxudicado",
                null,
                null,
                new OperadorEconomico(
                    operadorId,
                    "B12345678",
                    "Servizos Galegos SL",
                    new NomeRank(PUBLISHED_ON, 4711L),
                    Set.of()))));

    ContratoMenor stored = testRepository.findBySourceId(4711L).orElseThrow();

    OperadorEconomico awardee = stored.operadorEconomico();
    assertThat(awardee).isNotNull();
    assertThat(awardee.id()).isEqualTo(operadorId);
    assertThat(awardee.fiscalId()).isEqualTo("B12345678");
    assertThat(awardee.name()).isEqualTo("Servizos Galegos SL");
  }

  // Nothing derives an awardee yet, so a re-import carries none and the update leaves the column
  // alone rather than clearing it. Pinned as the behaviour it is, not as a rule: the derivation
  // task widens the update to repoint this key, and that is where this expectation changes.
  @Test
  void refreshing_contract_leaves_the_awardee_column_untouched() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorId operadorId = insertOperador("B12345678", "Servizos Galegos SL");
    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "As published")));
    assignAwardee(4711L, operadorId);

    contratoMenorRepository.upsertAll(List.of(contrato(4711L, organoId, "Corrected")));

    Table contratos = contratoTable();
    assertThat(contratos).hasNumberOfRows(1);
    assertThat(contratos)
        .row(0)
            .value("obxecto").isEqualTo("Corrected")
            .value("operador_economico_id").isEqualTo(operadorId.value());
  }

  private static ContratoMenor contrato(long sourceId, OrganoId organoId, String obxecto) {
    return new ContratoMenor(
        sourceId,
        organoId,
        PUBLISHED_ON,
        obxecto,
        new Money(new BigDecimal("1234.50")),
        "1 mes",
        null);
  }

  // Ordered by source_id so row(n) is stable, rather than leaning on uuidv7 insertion order.
  private Table contratoTable() {
    return AssertDbConnectionFactory.of(dataSource)
        .create()
        .table("contrato_menor")
        .columnsToOrder(new Table.Order[] {Table.Order.asc("source_id")})
        .build();
  }

  private UUID storedId(long sourceId) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT id FROM contrato_menor WHERE source_id = ?")) {
      statement.setLong(1, sourceId);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException(
              "No contract stored under source id %d".formatted(sourceId));
        }
        return resultSet.getObject("id", UUID.class);
      }
    }
  }

  private void assignAwardee(long sourceId, OperadorId operadorId) throws Exception {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement(
                "UPDATE contrato_menor SET operador_economico_id = ? WHERE source_id = ?")) {
      statement.setObject(1, operadorId.value());
      statement.setLong(2, sourceId);
      statement.executeUpdate();
    }
  }

  private OperadorId insertOperador(String fiscalId, String name) throws Exception {
    String sql =
        "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_date, name_rank_source_id)"
            + " VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, fiscalId);
      statement.setString(2, name);
      statement.setObject(3, Date.valueOf(PUBLISHED_ON));
      statement.setLong(4, 4711L);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OperadorId(resultSet.getObject("id", UUID.class));
      }
    }
  }

  // Neither helper commits: every test here expects its writes to succeed, and the injected
  // DataSource is Micronaut Data's connection-context-aware proxy, so the adapter under test
  // shares this connection and sees them uncommitted. The constraint-violation cases live in
  // ContratoMenorMigrationIntegrationTest, which commits for exactly that reason.
  private OrganoId insertOrgano(String sourceKey) throws Exception {
    String sql =
        "INSERT INTO organo_contratacion (id, source_key, name, active)"
            + " VALUES (uuidv7(), ?, ?, TRUE) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, sourceKey);
      statement.setString(2, sourceKey);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OrganoId(resultSet.getObject("id", UUID.class));
      }
    }
  }
}
