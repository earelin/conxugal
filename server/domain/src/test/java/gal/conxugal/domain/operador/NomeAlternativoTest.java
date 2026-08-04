package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NomeAlternativoTest {

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NomeAlternativo(null, null, RANK));
  }

  @Test
  void rejects_null_last_published_rank() {
    assertThatNullPointerException()
        .isThrownBy(() -> new NomeAlternativo(null, "Obradoiro Naval", null));
  }

  @Test
  void keeps_the_name_exactly_as_published() {
    NomeAlternativo retained = new NomeAlternativo(null, "  Obradoiro   Naval, S.L.  ", RANK);

    assertThat(retained.name()).isEqualTo("  Obradoiro   Naval, S.L.  ");
  }

  @Test
  void names_differing_only_in_letter_case_are_two_names() {
    NomeAlternativo upper = new NomeAlternativo(null, "OBRADOIRO NAVAL", RANK);
    NomeAlternativo mixed = new NomeAlternativo(null, "Obradoiro Naval", RANK);

    assertThat(upper).isNotEqualTo(mixed);
    assertThat(Set.of(upper, mixed)).hasSize(2);
  }

  @Test
  void names_differing_only_in_internal_spacing_are_two_names() {
    NomeAlternativo single = new NomeAlternativo(null, "Obradoiro Naval", RANK);
    NomeAlternativo doubled = new NomeAlternativo(null, "Obradoiro  Naval", RANK);

    assertThat(single).isNotEqualTo(doubled);
    assertThat(Set.of(single, doubled)).hasSize(2);
  }

  @Test
  void carries_the_rank_of_the_contract_that_last_published_it() {
    NomeAlternativo retained = new NomeAlternativo(null, "Obradoiro Naval", RANK);

    assertThat(retained.lastPublished()).isEqualTo(RANK);
  }

  @Test
  void allows_an_undated_rank() {
    NomeAlternativo retained =
        new NomeAlternativo(null, "Obradoiro Naval", new NomeRank(null, 17L));

    assertThat(retained.lastPublished().date()).isNull();
  }
}
