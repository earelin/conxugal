package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import gal.conxugal.domain.contrato.ContratosMenoresSection;
import gal.conxugal.domain.contrato.YearSelection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// What a summary means is settled by ContratosMenoresSummaryResponseTest — its years' order, its
// two flags and their serialisation. What is asserted here is only what this envelope adds: where
// the family's section is mounted, and that the summary travels under it untouched.
class ContratosMenoresFamilyResponseTest {

  @Test
  void mounts_the_family_at_its_own_route_segment() {
    ContratosMenoresFamilyResponse family = ContratosMenoresFamilyResponse.of(sectionOf(2025));

    assertThat(family.route()).isEqualTo("contratos-menores");
  }

  // Wiring only: that the section reaches the summary at all. What the mapping makes of it is
  // ContratosMenoresSummaryResponseTest's subject and is not restated here.
  @Test
  void hands_the_section_to_the_summary_that_publishes_it() {
    ContratosMenoresFamilyResponse family = ContratosMenoresFamilyResponse.of(sectionOf(2025));

    assertThat(family.summary().years()).containsExactly(2025);
  }

  // The structural decision this record exists for. Flattening the summary's fields beside the
  // route reads as the simpler payload and is the refactor someone will reach for, but it would
  // put `route` — the page's field — into ContratosMenoresSummary, which is FEAT-0011's schema and
  // which this operation must $ref rather than restate. Asserting the exact key set is what makes
  // that boundary fail loudly instead of drifting.
  @Test
  void nests_the_summary_rather_than_flattening_it_beside_the_route() throws IOException {
    ContratosMenoresFamilyResponse family = ContratosMenoresFamilyResponse.of(sectionOf(2025));

    Map<String, Object> payload = serialised(family);

    assertThat(payload)
        .containsOnlyKeys("route", "summary")
        .containsEntry("route", "contratos-menores");
    assertThat(payload)
        .extracting("summary", MAP)
        .containsOnlyKeys("years", "partial", "updating");
  }

  private static ContratosMenoresSection sectionOf(int year) {
    return new ContratosMenoresSection(List.of(YearSelection.of(year)), false, true);
  }

  private static Map<String, Object> serialised(ContratosMenoresFamilyResponse family)
      throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    String json = objectMapper.writeValueAsString(family);

    return objectMapper.readValue(json, Argument.mapOf(String.class, Object.class));
  }
}
