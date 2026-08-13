package gal.conxugal.infrastructure.jdbc.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratoMenor;
import gal.conxugal.domain.contrato.ContratoMenorRepository;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.operador.OperadorId;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganosWithVisibleContracts;
import gal.conxugal.infrastructure.jdbc.support.DatabaseCleanup;
import gal.conxugal.infrastructure.jdbc.support.PostgresContainer;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The contratos menores family's answer to the visible-set question, against a real PostgreSQL.
 * The port is injected rather than the adapter class, so what is exercised is the contract the
 * catalogue read depends on and not a method that happens to be public.
 *
 * <p>A contract is visible only when it carries all three of a publication date, an amount and an
 * awardee, so each of the three is withheld in a case of its own: two null checks passing and the
 * third missing is precisely the defect a single "incomplete contract" case would not see.
 */
@MicronautTest(startApplication = false)
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcContratoMenorVisibleOrganosIntegrationTest implements TestPropertyProvider {

  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 3, 14);
  private static final Money AMOUNT = new Money(new BigDecimal("1234.50"));
  private static final String OPERADOR_NAME = "Servizos Galegos SL";
  private static final String FISCAL_ID = "B12345678";

  @Container
  static PostgreSQLContainer<?> postgres = PostgresContainer.create();

  @Override
  public @NonNull Map<String, String> getProperties() {
    return PostgresContainer.datasourceProperties(postgres);
  }

  @Inject
  OrganosWithVisibleContracts organosWithVisibleContracts;

  @Inject
  ContratoMenorRepository contratoMenorRepository;

  @Inject
  DataSource dataSource;

  @AfterEach
  void cleanUp() throws Exception {
    DatabaseCleanup.truncateAllTables(dataSource);
  }

  @Test
  void answers_an_organo_holding_one_complete_contract() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(contrato(4711L, organoId, PUBLISHED_ON, AMOUNT, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(organoId));

    assertThat(visible).containsExactly(organoId);
  }

  @Test
  void withholds_an_organo_whose_only_contract_has_no_publication_date() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(contrato(4711L, organoId, null, AMOUNT, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(organoId));

    assertThat(visible).isEmpty();
  }

  @Test
  void withholds_an_organo_whose_only_contract_has_no_amount() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(contrato(4711L, organoId, PUBLISHED_ON, null, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(organoId));

    assertThat(visible).isEmpty();
  }

  @Test
  void withholds_an_organo_whose_only_contract_has_no_awardee() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    store(contrato(4711L, organoId, PUBLISHED_ON, AMOUNT, null));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(organoId));

    assertThat(visible).isEmpty();
  }

  // An Órgano all of whose contracts are anomalous is answered exactly as one holding none, so
  // both a complete contract elsewhere and an Órgano with no rows at all are in the same case.
  @Test
  void withholds_an_organo_holding_no_contract_while_answering_one_that_does() throws Exception {
    OrganoId visibleOrgano = insertOrgano("consorcio-x");
    OrganoId emptyOrgano = insertOrgano("axencia-y");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(contrato(4711L, visibleOrgano, PUBLISHED_ON, AMOUNT, awardee));

    Set<OrganoId> visible =
        organosWithVisibleContracts.among(List.of(visibleOrgano, emptyOrgano));

    assertThat(visible).containsExactly(visibleOrgano);
  }

  @Test
  void answers_an_organo_holding_one_complete_contract_among_anomalous_ones() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(
        contrato(4711L, organoId, null, AMOUNT, awardee),
        contrato(4712L, organoId, PUBLISHED_ON, null, awardee),
        contrato(4713L, organoId, PUBLISHED_ON, AMOUNT, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(organoId));

    assertThat(visible).containsExactly(organoId);
  }

  // Answered without a round trip, and asserted with a visible contract stored so a short-circuit
  // that leaked the whole family's answer would show up here rather than reading as empty.
  @Test
  void answers_nothing_for_an_empty_candidate_set() throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(contrato(4711L, organoId, PUBLISHED_ON, AMOUNT, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of());

    assertThat(visible).isEmpty();
  }

  // The answer is scoped to what was asked, so a caller holding a narrowed catalogue is never
  // handed an Órgano back that was not in it.
  @Test
  void answers_only_about_the_organos_it_was_asked() throws Exception {
    OrganoId asked = insertOrgano("consorcio-x");
    OrganoId notAsked = insertOrgano("axencia-y");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    store(
        contrato(4711L, asked, PUBLISHED_ON, AMOUNT, awardee),
        contrato(4712L, notAsked, PUBLISHED_ON, AMOUNT, awardee));

    Set<OrganoId> visible = organosWithVisibleContracts.among(List.of(asked));

    assertThat(visible).containsExactly(asked);
  }

  // The entering half of the rule: no administrator action and no catalogue import between the
  // two reads, only the contract itself.
  @Test
  void an_organo_enters_the_visible_set_when_its_first_complete_contract_is_stored()
      throws Exception {
    OrganoId organoId = insertOrgano("consorcio-x");
    OperadorEconomico awardee = insertOperador(FISCAL_ID);
    assertThat(organosWithVisibleContracts.among(List.of(organoId))).isEmpty();

    store(contrato(4711L, organoId, PUBLISHED_ON, AMOUNT, awardee));

    assertThat(organosWithVisibleContracts.among(List.of(organoId))).containsExactly(organoId);
  }

  private void store(ContratoMenor... contratos) {
    contratoMenorRepository.upsertAll(List.of(contratos));
  }

  private static ContratoMenor contrato(
      long sourceId,
      OrganoId organoId,
      LocalDate publicationDate,
      Money amount,
      OperadorEconomico awardee) {
    return new ContratoMenor(
        sourceId,
        organoId,
        publicationDate,
        "Subministración de material",
        amount,
        "1 mes",
        awardee);
  }

  // Returns the awardee it stored rather than only its id, so a contract can never be built
  // against a fiscal identifier the row does not carry.
  private OperadorEconomico insertOperador(String fiscalId) throws Exception {
    String sql =
        "INSERT INTO operador_economico (id, fiscal_id, name, name_rank_date, name_rank_source_id)"
            + " VALUES (uuidv7(), ?, ?, ?, ?) RETURNING id";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, fiscalId);
      statement.setString(2, OPERADOR_NAME);
      statement.setObject(3, Date.valueOf(PUBLISHED_ON));
      statement.setLong(4, 4711L);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new IllegalStateException("Insert did not return a generated id");
        }
        return new OperadorEconomico(
            new OperadorId(resultSet.getObject("id", UUID.class)),
            new FiscalIdentifier(fiscalId),
            OPERADOR_NAME,
            new NomeRank(PUBLISHED_ON, 4711L),
            Set.of());
      }
    }
  }

  // Neither helper commits: the injected DataSource is Micronaut Data's connection-context-aware
  // proxy, so the adapter under test shares this connection and sees the rows uncommitted.
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
