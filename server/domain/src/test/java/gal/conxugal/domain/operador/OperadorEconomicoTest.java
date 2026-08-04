package gal.conxugal.domain.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperadorEconomicoTest {

  private static final NomeRank RANK = new NomeRank(LocalDate.of(2026, 3, 14), 4242L);

  @Test
  void holds_the_canonical_identifier_whatever_padding_and_case_it_was_built_from() {
    OperadorEconomico operador =
        new OperadorEconomico("  b12345678 ", "Obradoiro Naval", RANK);

    assertThat(operador.fiscalId()).isEqualTo("B12345678");
  }

  @Test
  void keeps_no_accessor_returning_the_identifier_spelling_it_was_built_from() {
    OperadorEconomico operador =
        new OperadorEconomico(" b12345678 ", "Obradoiro Naval", RANK);

    assertThat(operador.fiscalId())
        .isNotEqualTo(" b12345678 ")
        .isNotEqualTo("b12345678");
  }

  @Test
  void canonicalising_an_identifier_touches_nothing_but_padding_and_case() {
    OperadorEconomico spaced = new OperadorEconomico("B1234 5678", "Obradoiro Naval", RANK);
    OperadorEconomico punctuated = new OperadorEconomico("B-12345678", "Obradoiro Naval", RANK);

    assertThat(spaced.fiscalId()).isEqualTo("B1234 5678");
    assertThat(punctuated.fiscalId()).isEqualTo("B-12345678");
  }

  @Test
  void the_identity_is_distinct_from_the_fiscal_identifier() {
    OperadorId id = new OperadorId(UUID.randomUUID());
    OperadorEconomico operador =
        new OperadorEconomico(id, "B12345678", "Obradoiro Naval", RANK, Set.of());

    assertThat(operador.id()).isEqualTo(id);
    assertThat(operador.fiscalId()).isEqualTo("B12345678");
  }

  @Test
  void keeps_the_name_exactly_as_published() {
    OperadorEconomico operador =
        new OperadorEconomico("B12345678", "obradoiro   Naval, S.L.", RANK);

    assertThat(operador.name()).isEqualTo("obradoiro   Naval, S.L.");
  }

  @Test
  void carries_the_rank_of_the_contract_the_name_came_from() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);

    assertThat(operador.nameRank()).isEqualTo(RANK);
  }

  @Test
  void allows_null_id_before_being_persisted() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);

    assertThat(operador.id()).isNull();
  }

  @Test
  void newly_catalogued_operador_has_borne_no_other_name() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);

    assertThat(operador.nomesAlternativos()).isEmpty();
  }

  @Test
  void rejects_null_fiscal_id() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico(null, "Obradoiro Naval", RANK));
  }

  @Test
  void rejects_null_name() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico("B12345678", null, RANK));
  }

  @Test
  void rejects_null_name_rank() {
    assertThatNullPointerException()
        .isThrownBy(() -> new OperadorEconomico("B12345678", "Obradoiro Naval", null));
  }

  @Test
  void rejects_an_alternative_equal_to_the_principal_name() {
    Set<NomeAlternativo> alternativos = Set.of(new NomeAlternativo("Obradoiro Naval", RANK));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new OperadorEconomico(
                    null, "B12345678", "Obradoiro Naval", RANK, alternativos));
  }

  @Test
  void an_alternative_differing_from_the_principal_only_in_case_is_allowed() {
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            "B12345678",
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo("OBRADOIRO NAVAL", RANK)));

    assertThat(operador.nomesAlternativos()).hasSize(1);
  }

  @Test
  void advancing_the_display_moves_the_name_and_the_rank_together() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);
    NomeRank newer = new NomeRank(LocalDate.of(2026, 7, 1), 5100L);

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval, S.L.", newer);

    assertThat(advanced.name()).isEqualTo("Obradoiro Naval, S.L.");
    assertThat(advanced.nameRank()).isEqualTo(newer);
  }

  @Test
  void advancing_the_display_retains_the_name_it_displaced_under_the_rank_it_won_with() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);

    OperadorEconomico advanced =
        operador.displaying("Obradoiro Naval, S.L.", new NomeRank(LocalDate.of(2026, 7, 1), 5100L));

    assertThat(advanced.nomesAlternativos())
        .containsExactly(new NomeAlternativo(null, "Obradoiro Naval", RANK));
  }

  @Test
  void advancing_the_display_leaves_the_new_name_absent_from_the_alternatives() {
    NomeRank older = new NomeRank(LocalDate.of(2025, 1, 9), 900L);
    OperadorEconomico operador =
        new OperadorEconomico(
            null,
            "B12345678",
            "Obradoiro Naval",
            RANK,
            Set.of(new NomeAlternativo("Obradoiro Naval, S.L.", older)));

    OperadorEconomico advanced =
        operador.displaying("Obradoiro Naval, S.L.", new NomeRank(LocalDate.of(2026, 7, 1), 5100L));

    assertThat(advanced.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .containsExactly("Obradoiro Naval");
  }

  @Test
  void republishing_the_displayed_name_advances_the_rank_and_retains_nothing() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);
    NomeRank newer = new NomeRank(LocalDate.of(2026, 7, 1), 5100L);

    OperadorEconomico advanced = operador.displaying("Obradoiro Naval", newer);

    assertThat(advanced.nameRank()).isEqualTo(newer);
    assertThat(advanced.nomesAlternativos()).isEmpty();
  }

  @Test
  void advancing_the_display_never_leaves_an_alternative_equal_to_the_principal() {
    OperadorEconomico operador = new OperadorEconomico("B12345678", "Obradoiro Naval", RANK);

    OperadorEconomico advanced =
        operador.displaying("Obradoiro Naval, S.L.", new NomeRank(LocalDate.of(2026, 7, 1), 5100L));

    assertThat(advanced.nomesAlternativos())
        .extracting(NomeAlternativo::name)
        .doesNotContain(advanced.name());
  }
}
