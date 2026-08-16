package gal.conxugal.application.rest.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.VisibleContratoMenor;
import gal.conxugal.domain.money.Money;
import gal.conxugal.domain.operador.FiscalIdentifier;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ContratoMenorResponseTest {

  private static final ContratosMenoresPublicationConfiguration PUBLICATION =
      new ContratosMenoresPublicationConfiguration("https://www.contratosdegalicia.gal");

  @Test
  void unwraps_the_typed_values_one_browse_row_carries() {
    ContratoMenorResponse response = ContratoMenorResponse.of(row("materiais", "1 mes"),
        PUBLICATION);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.sourceId()).isEqualTo(1234567L);
      softly.assertThat(response.publicationDate()).isEqualTo(LocalDate.of(2025, 3, 14));
      softly.assertThat(response.obxecto()).isEqualTo("materiais");
      softly.assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("14520.75"));
      softly.assertThat(response.duration()).isEqualTo("1 mes");
      softly.assertThat(response.awardee().name()).isEqualTo("Subministracións Galegas, S.L.");
      softly.assertThat(response.awardee().fiscalId()).isEqualTo("B12345678");
    });
  }

  // The published scale is part of the figure: an amount stored as 14520.70 is not the same
  // published value as 14520.7, and nothing between the store and the wire may rescale it.
  @Test
  void keeps_the_amount_at_the_scale_it_was_published_with() {
    ContratoMenorResponse response = ContratoMenorResponse.of(
        new VisibleContratoMenor(1234567L, LocalDate.of(2025, 3, 14), "materiais",
            new Money(new BigDecimal("14520.70")), "1 mes", "Subministracións Galegas, S.L.",
            new FiscalIdentifier("B12345678")),
        PUBLICATION);

    assertThat(response.amount().toPlainString()).isEqualTo("14520.70");
  }

  // The identifier is canonical at the domain, and the wire carries that form rather than
  // whatever the caller of this mapping happened to hold.
  @Test
  void carries_the_awardees_canonical_fiscal_identifier() {
    ContratoMenorResponse response = ContratoMenorResponse.of(
        new VisibleContratoMenor(1234567L, LocalDate.of(2025, 3, 14), "materiais",
            new Money(new BigDecimal("1.00")), null, "Subministracións Galegas, S.L.",
            new FiscalIdentifier("  b12345678 ")),
        PUBLICATION);

    assertThat(response.awardee().fiscalId()).isEqualTo("B12345678");
  }

  @Test
  void composes_the_source_link_from_the_configured_publication_host() {
    ContratoMenorResponse response = ContratoMenorResponse.of(row("materiais", "1 mes"),
        PUBLICATION);

    assertThat(response.sourceUrl())
        .isEqualTo("https://www.contratosdegalicia.gal/licitacion?N=1234567");
  }

  // The link is composed from this feature's own property, and this is what fails if someone
  // reaches for the import client's base URL instead — that one is pointed at a stub in every
  // environment but production.
  @Test
  void composes_the_source_link_from_whatever_host_it_is_handed() {
    ContratoMenorResponse response = ContratoMenorResponse.of(row("materiais", "1 mes"),
        new ContratosMenoresPublicationConfiguration("https://example.test"));

    assertThat(response.sourceUrl()).isEqualTo("https://example.test/licitacion?N=1234567");
  }

  // The mapping producing nulls is only half of it: the serializer's default inclusion drops a
  // null, which would take both keys out of a payload the contract declares them required in —
  // and would make an award the source published no object for indistinguishable from a field
  // the server forgot. Asserting the serialised keys is what makes @JsonInclude(ALWAYS)
  // load-bearing rather than decorative.
  @Test
  void serialises_an_absent_obxecto_and_duration_as_explicit_nulls() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    ContratoMenorResponse response = ContratoMenorResponse.of(row(null, null), PUBLICATION);

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap))
        .containsEntry("obxecto", null)
        .containsEntry("duration", null);
  }

  // Every row of this list belongs to the Órgano already open, and nothing consumes an operador
  // identity until the operadores read exists. Both absences are asserted on the serialised keys,
  // because a test naming the fields it wants cannot see one it did not ask for.
  @Test
  void states_neither_the_awarding_organo_nor_an_operador_identity() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    ContratoMenorResponse response = ContratoMenorResponse.of(row("materiais", "1 mes"),
        PUBLICATION);

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap))
        .doesNotContainKeys("organo", "organoId", "operadorId", "operadorEconomicoId");
  }

  private static VisibleContratoMenor row(@Nullable String obxecto, @Nullable String duration) {
    return new VisibleContratoMenor(
        1234567L,
        LocalDate.of(2025, 3, 14),
        obxecto,
        new Money(new BigDecimal("14520.75")),
        duration,
        "Subministracións Galegas, S.L.",
        new FiscalIdentifier("B12345678"));
  }
}
