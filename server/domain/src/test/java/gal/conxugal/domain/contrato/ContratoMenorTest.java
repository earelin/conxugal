package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.NomeRank;
import gal.conxugal.domain.operador.OperadorEconomico;
import gal.conxugal.domain.organo.OrganoId;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContratoMenorTest {

  private static final long SOURCE_ID = 2001090L;
  private static final OrganoId ORGANO_ID =
      new OrganoId(UUID.fromString("0198c0de-0000-7000-8000-00000000002a"));
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2026, 5, 5);
  private static final Money AMOUNT = new Money(new BigDecimal("3630.00"));
  private static final OperadorEconomico OPERADOR =
      new OperadorEconomico(
          "B12345678", "Obradoiro Naval, S.L.", new NomeRank(PUBLISHED_ON, SOURCE_ID));

  @Test
  void carries_no_identity_until_the_database_assigns_one() {
    ContratoMenor contrato = published("Servizos técnicos de electricidade", "1 mes");

    assertThat(contrato.id()).isNull();
  }

  @Test
  void carries_an_identity_distinct_from_the_source_identifier() {
    ContratoMenorId id = new ContratoMenorId(UUID.randomUUID());

    ContratoMenor contrato = stored(id, null);

    assertThat(contrato.id()).isEqualTo(id);
    assertThat(contrato.sourceId()).isEqualTo(SOURCE_ID);
  }

  @Test
  void requires_the_awarding_organo() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new ContratoMenor(SOURCE_ID, null, PUBLISHED_ON, "Obras", AMOUNT, "1 mes", null));
  }

  @Test
  void stores_an_award_the_source_published_nothing_else_about() {
    ContratoMenor contrato =
        new ContratoMenor(SOURCE_ID, ORGANO_ID, null, null, null, null, null);

    assertThat(contrato.publicationDate()).isNull();
    assertThat(contrato.obxecto()).isNull();
    assertThat(contrato.amount()).isNull();
    assertThat(contrato.duration()).isNull();
    assertThat(contrato.operadorEconomico()).isNull();
  }

  @Test
  void keeps_the_object_exactly_as_published_including_internal_spacing() {
    String obxecto = "SERVIZOS   TÉCNICOS de  Electricidade";

    assertThat(published(obxecto, "1 mes").obxecto())
        .isEqualTo(obxecto);
  }

  @Test
  void keeps_the_object_at_whatever_length_the_source_published() {
    String obxecto = "Servizos de mantemento ".repeat(200);

    assertThat(published(obxecto, "1 mes").obxecto())
        .isEqualTo(obxecto)
        .hasSize(obxecto.length());
  }

  @Test
  void keeps_the_duration_exactly_as_the_adapter_handed_it_over() {
    assertThat(published("Obras", "  1 mes  ").duration())
        .isEqualTo("  1 mes  ");
  }

  @Test
  void caps_no_duration_of_its_own_because_the_adapter_already_did() {
    String duration = "un".repeat(100);

    assertThat(published("Obras", duration).duration())
        .isEqualTo(duration);
  }

  @Test
  void holds_an_object_that_published_only_whitespace_as_null() {
    assertThat(published("   ", "1 mes").obxecto())
        .isNull();
  }

  @Test
  void holds_the_duration_that_published_only_whitespace_as_null() {
    assertThat(published("Obras", " \t").duration())
        .isNull();
  }

  @Test
  void keeps_an_uninterpretable_publication_date_null_without_refusing_the_contract() {
    ContratoMenor contrato =
        new ContratoMenor(SOURCE_ID, ORGANO_ID, null, "Obras", AMOUNT, "1 mes", OPERADOR);

    assertThat(contrato.publicationDate()).isNull();
    assertThat(contrato.obxecto()).isEqualTo("Obras");
  }

  @Test
  void keeps_an_absent_amount_null_without_refusing_the_contract() {
    ContratoMenor contrato =
        new ContratoMenor(SOURCE_ID, ORGANO_ID, PUBLISHED_ON, "Obras", null, "1 mes", OPERADOR);

    assertThat(contrato.amount()).isNull();
    assertThat(contrato.publicationDate()).isEqualTo(PUBLISHED_ON);
  }

  @Test
  void keeps_the_amount_exactly_as_published() {
    assertThat(published("Obras", "1 mes").amount())
        .isEqualTo(new Money(new BigDecimal("3630.00")));
  }

  @Test
  void reaches_the_awardee_through_one_association() {
    assertThat(published("Obras", "1 mes").operadorEconomico())
        .isEqualTo(OPERADOR);
  }

  @Test
  void stores_an_award_whose_identifier_was_unusable_under_no_operador() {
    ContratoMenor contrato =
        new ContratoMenor(SOURCE_ID, ORGANO_ID, PUBLISHED_ON, "Obras", AMOUNT, "1 mes", null);

    assertThat(contrato.operadorEconomico()).isNull();
    assertThat(contrato.sourceId()).isEqualTo(SOURCE_ID);
  }

  @Test
  void holds_no_awardee_name_or_fiscal_identifier_of_its_own() {
    // The awardee lives once, on the operador row. A component reintroducing either here would
    // renormalise the schema by accident and reinstate the per-contract variance the specs were
    // amended to give up.
    assertThat(
            Arrays.stream(ContratoMenor.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "id",
            "sourceId",
            "organoId",
            "publicationDate",
            "obxecto",
            "amount",
            "duration",
            "operadorEconomico");
  }

  @Test
  void treats_two_readings_of_one_stored_contract_as_the_same_contract() {
    ContratoMenorId id = new ContratoMenorId(UUID.randomUUID());

    ContratoMenor read = stored(id, null);
    ContratoMenor reread = stored(id, null);

    assertThat(read)
        .isEqualTo(reread)
        .hasSameHashCodeAs(reread);
  }

  @Test
  void stays_the_same_contract_when_its_operador_row_changes_underneath() {
    ContratoMenorId id = new ContratoMenorId(UUID.randomUUID());
    OperadorEconomico renamed =
        OPERADOR.displaying("Obradoiro Naval, Sociedade Limitada", new NomeRank(null, 2001110L));

    ContratoMenor beforeRename = stored(id, OPERADOR);
    ContratoMenor afterRename = stored(id, renamed);

    // Contents-based equality would compare the awardee, and through it every name the operador
    // has been published under, so the same row would stop being equal to itself.
    assertThat(beforeRename)
        .isEqualTo(afterRename)
        .hasSameHashCodeAs(afterRename);
  }

  @Test
  void separates_contracts_carrying_different_identities() {
    ContratoMenor first = stored(new ContratoMenorId(UUID.randomUUID()), null);
    ContratoMenor second = stored(new ContratoMenorId(UUID.randomUUID()), null);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void treats_contracts_the_database_has_not_identified_as_distinct() {
    ContratoMenor first = published("Obras", "1 mes");
    ContratoMenor second = published("Obras", "1 mes");

    assertThat(first)
        .isNotEqualTo(second);
    // Two readings of the same publication, neither stored yet: each is only itself, so the same
    // instance twice collapses and the two distinct ones do not.
    assertThat(new HashSet<>(List.of(first, second, first)))
        .hasSize(2);
  }

  @Test
  void separates_an_identified_contract_from_one_not_yet_stored() {
    ContratoMenor identified = stored(new ContratoMenorId(UUID.randomUUID()), OPERADOR);

    assertThat(identified).isNotEqualTo(published("Obras", "1 mes"));
  }

  private static ContratoMenor published(String obxecto, String duration) {
    return new ContratoMenor(
        SOURCE_ID, ORGANO_ID, PUBLISHED_ON, obxecto, AMOUNT, duration, OPERADOR);
  }

  private static ContratoMenor stored(ContratoMenorId id, OperadorEconomico operador) {
    return new ContratoMenor(
        id, SOURCE_ID, ORGANO_ID, PUBLISHED_ON, "Obras", AMOUNT, "1 mes", operador);
  }
}
