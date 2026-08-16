package gal.conxugal.application.rest.contratosmenores;

import static org.assertj.core.api.Assertions.assertThat;

import gal.conxugal.domain.contrato.ContratosMenoresSection;
import gal.conxugal.domain.contrato.YearSelection;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class ContratosMenoresSummaryResponseTest {

  @Test
  void keeps_the_newest_first_order_the_years_arrived_in() {
    ContratosMenoresSummaryResponse response = ContratosMenoresSummaryResponse.of(
        new ContratosMenoresSection(
            List.of(YearSelection.of(2025), YearSelection.of(2024), YearSelection.of(2019)), false,
            false));

    assertThat(response.years()).containsExactly(2025, 2024, 2019);
  }

  // The two flags say different things — what is shown is incomplete, and it is still being
  // refreshed — so all four pairings have to survive the mapping. Asserting them together is what
  // catches one being read off the other, which a single enum on the wire would have forced.
  @Test
  void carries_the_two_flags_independently_of_each_other() {
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(summaryOf(true, true).partial()).isTrue();
      softly.assertThat(summaryOf(true, true).updating()).isTrue();
      softly.assertThat(summaryOf(true, false).partial()).isTrue();
      softly.assertThat(summaryOf(true, false).updating()).isFalse();
      softly.assertThat(summaryOf(false, true).partial()).isFalse();
      softly.assertThat(summaryOf(false, true).updating()).isTrue();
      softly.assertThat(summaryOf(false, false).partial()).isFalse();
      softly.assertThat(summaryOf(false, false).updating()).isFalse();
    });
  }

  // The contract declares all three properties required, and the serializer's default inclusion
  // is what could quietly take one out of the payload. A complete, no-longer-updated Órgano is
  // the summary that would lose both flags, so it is the one asserted on the serialized keys
  // rather than on the record's fields.
  @Test
  void serialises_every_property_when_neither_flag_is_set() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();

    String json = objectMapper.writeValueAsString(summaryOf(false, false));

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    Map<String, Object> payload = objectMapper.readValue(json, asMap);
    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(payload).containsEntry("years", List.of(2025));
      softly.assertThat(payload).containsEntry("partial", false);
      softly.assertThat(payload).containsEntry("updating", false);
    });
  }

  @Test
  void does_not_share_the_list_of_years_it_was_built_from() {
    List<Integer> years = new ArrayList<>(List.of(2025, 2024));

    ContratosMenoresSummaryResponse response =
        new ContratosMenoresSummaryResponse(years, false, false);
    years.add(2023);

    assertThat(response.years()).containsExactly(2025, 2024);
  }

  private static ContratosMenoresSummaryResponse summaryOf(boolean partial, boolean updating) {
    return ContratosMenoresSummaryResponse.of(
        new ContratosMenoresSection(List.of(YearSelection.of(2025)), partial, updating));
  }
}
