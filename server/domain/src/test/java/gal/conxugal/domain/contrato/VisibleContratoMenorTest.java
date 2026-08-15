package gal.conxugal.domain.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class VisibleContratoMenorTest {

  private static final long SOURCE_ID = 42L;
  private static final LocalDate PUBLISHED_ON = LocalDate.of(2025, 3, 14);
  private static final String OBXECTO = "Subministro";
  private static final Money AMOUNT = new Money(new BigDecimal("3630.00"));
  private static final String DURATION = "12 meses";
  private static final String AWARDEE_NAME = "Acme SL";
  private static final FiscalIdentifier AWARDEE_FISCAL_ID = new FiscalIdentifier("B12345678");

  @Test
  void refuses_construction_without_the_publication_date() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new VisibleContratoMenor(
                    SOURCE_ID, null, OBXECTO, AMOUNT, DURATION, AWARDEE_NAME, AWARDEE_FISCAL_ID));
  }

  @Test
  void refuses_construction_without_the_amount() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new VisibleContratoMenor(
                    SOURCE_ID,
                    PUBLISHED_ON,
                    OBXECTO,
                    null,
                    DURATION,
                    AWARDEE_NAME,
                    AWARDEE_FISCAL_ID));
  }

  @Test
  void refuses_construction_without_the_awardee_name() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new VisibleContratoMenor(
                    SOURCE_ID, PUBLISHED_ON, OBXECTO, AMOUNT, DURATION, null, AWARDEE_FISCAL_ID));
  }

  @Test
  void refuses_construction_without_the_awardee_fiscal_identifier() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new VisibleContratoMenor(
                    SOURCE_ID, PUBLISHED_ON, OBXECTO, AMOUNT, DURATION, AWARDEE_NAME, null));
  }

  @Test
  void permits_the_two_values_the_source_genuinely_leaves_unpublished() {
    VisibleContratoMenor contrato =
        new VisibleContratoMenor(
            SOURCE_ID, PUBLISHED_ON, null, AMOUNT, null, AWARDEE_NAME, AWARDEE_FISCAL_ID);

    assertThat(contrato.obxecto())
        .isNull();
    assertThat(contrato.duration())
        .isNull();
  }

  @Test
  void carries_every_value_exactly_as_it_was_handed_them() {
    VisibleContratoMenor contrato =
        new VisibleContratoMenor(
            SOURCE_ID, PUBLISHED_ON, OBXECTO, AMOUNT, DURATION, AWARDEE_NAME, AWARDEE_FISCAL_ID);

    assertThat(contrato)
        .extracting(
            VisibleContratoMenor::sourceId,
            VisibleContratoMenor::publicationDate,
            VisibleContratoMenor::obxecto,
            VisibleContratoMenor::amount,
            VisibleContratoMenor::duration,
            VisibleContratoMenor::awardeeName,
            VisibleContratoMenor::awardeeFiscalId)
        .containsExactly(
            SOURCE_ID, PUBLISHED_ON, OBXECTO, AMOUNT, DURATION, AWARDEE_NAME, AWARDEE_FISCAL_ID);
  }
}
