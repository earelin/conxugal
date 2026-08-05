package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NomeAlternativoTest {

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);
  private static final NomeRank OLDER = new NomeRank(LocalDate.of(2025, 1, 9), 900L);

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

  @Test
  void the_same_name_under_two_ranks_is_one_retained_name() {
    NomeAlternativo older = new NomeAlternativo(null, "Obradoiro Naval", OLDER);
    NomeAlternativo newer = new NomeAlternativo(null, "Obradoiro Naval", RANK);

    assertThat(older).isEqualTo(newer);
    assertThat(new HashSet<>(List.of(older, newer))).hasSize(1);
  }

  /**
   * The operador column files the row, it does not identify the value — so two values never meet
   * unless they are already inside the same aggregate, and comparing across operadores is not a
   * question this type answers.
   */
  @Test
  void the_operador_the_name_is_filed_under_does_not_enter_into_the_comparison() {
    NomeAlternativo unattached = new NomeAlternativo(null, "Obradoiro Naval", RANK);
    NomeAlternativo attached =
        new NomeAlternativo(new OperadorId(UUID.randomUUID()), "Obradoiro Naval", RANK);

    assertThat(unattached).isEqualTo(attached);
    assertThat(unattached).hasSameHashCodeAs(attached);
  }

  @Test
  void the_same_name_holds_its_hash_across_ranks() {
    NomeAlternativo older = new NomeAlternativo(null, "Obradoiro Naval", OLDER);
    NomeAlternativo newer = new NomeAlternativo(null, "Obradoiro Naval", RANK);

    assertThat(older).hasSameHashCodeAs(newer);
  }
}
