package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NomeRankingTest {

  private static final FiscalIdentifier FISCAL_ID = new FiscalIdentifier("B12345678");

  private static final NomeRank EARLIER = new NomeRank(LocalDate.of(2025, 1, 9), 900L);
  private static final NomeRank LATER = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

  @Test
  void later_publication_date_outranks_an_earlier_one() {
    assertThat(NomeRanking.outranks(LATER, EARLIER)).isTrue();
  }

  @Test
  void earlier_publication_date_never_outranks_the_later_one() {
    assertThat(NomeRanking.outranks(EARLIER, LATER)).isFalse();
  }

  @Test
  void date_tie_is_settled_on_the_higher_source_identifier() {
    NomeRank higher = new NomeRank(LocalDate.of(2026, 3, 14), 5100L);
    NomeRank lower = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

    assertThat(NomeRanking.outranks(higher, lower)).isTrue();
    assertThat(NomeRanking.outranks(lower, higher)).isFalse();
  }

  @Test
  void dated_contract_outranks_an_undated_one() {
    assertThat(NomeRanking.outranks(EARLIER, new NomeRank(null, 17L))).isTrue();
  }

  @Test
  void undated_contract_never_outranks_the_dated_one_however_late_it_arrives() {
    NomeRank undated = new NomeRank(null, Long.MAX_VALUE);

    assertThat(NomeRanking.outranks(undated, EARLIER)).isFalse();
  }

  @Test
  void two_undated_contracts_are_settled_on_the_higher_source_identifier() {
    NomeRank higher = new NomeRank(null, 5100L);
    NomeRank lower = new NomeRank(null, 900L);

    assertThat(NomeRanking.outranks(higher, lower)).isTrue();
    assertThat(NomeRanking.outranks(lower, higher)).isFalse();
  }

  @Test
  void rank_does_not_outrank_itself() {
    assertThat(NomeRanking.outranks(LATER, LATER)).isFalse();
  }

  @Test
  void two_readings_of_one_contract_do_not_outrank_each_other() {
    NomeRank read = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
    NomeRank reread = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

    assertThat(NomeRanking.outranks(read, reread)).isFalse();
    assertThat(NomeRanking.outranks(reread, read)).isFalse();
  }

  @Test
  void displayed_name_outranks_the_name_retained_at_its_date_and_lower_source_identifier() {
    NomeRank sharedDateLowerSourceId = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            new NomeRank(LocalDate.of(2026, 3, 14), 5100L),
            Set.of(
                new NomeAlternativo(
                    null, "Obradoiro Naval, S.L.", sharedDateLowerSourceId)));
    NomeAlternativo retained = operador.nomesAlternativos().iterator().next();

    assertThat(NomeRanking.outranks(operador.nameRank(), retained.lastPublished())).isTrue();
  }

  @Test
  void displayed_name_outranks_the_name_retained_from_an_undated_contract() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            FISCAL_ID,
            "Obradoiro Naval",
            EARLIER,
            Set.of(
                new NomeAlternativo(null, "Obradoiro Naval, S.L.", new NomeRank(null, 5100L))));
    NomeAlternativo retained = operador.nomesAlternativos().iterator().next();

    assertThat(NomeRanking.outranks(operador.nameRank(), retained.lastPublished())).isTrue();
  }
}
