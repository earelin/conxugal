package gal.conxugal.application.rest.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import gal.conxugal.application.rest.contratosmenores.ContratosMenoresSummaryResponse;
import gal.conxugal.domain.contrato.ContratosMenoresSection;
import gal.conxugal.domain.contrato.YearSelection;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class OrganoMemberResponseTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());

  @Test
  void carries_the_organos_identity_and_name_beside_its_families() {
    OrganoMemberResponse response =
        OrganoMemberResponse.of(sergas(), familiesWithContratosMenores());

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.id()).isEqualTo(ORGANO_ID.value());
      softly.assertThat(response.name()).isEqualTo("Servizo Galego de Saúde");
      softly.assertThat(response.families().contratosMenores().years())
          .containsExactly(2025, 2024, 2023);
    });
  }

  // The page renders "this Órgano holds no contracts" from an empty families map, so an absent key
  // and an empty one are different facts.
  @Test
  void serialises_an_empty_families_map_rather_than_dropping_it() throws IOException {
    OrganoMemberResponse response = OrganoMemberResponse.of(sergas(), new FamiliesResponse(null));

    assertThat(serialised(response)).containsEntry("families", Map.of());
  }

  // The other half of the same payload: families itself must be there, and a family it does not
  // hold must not be. A contratos-menores key sent as null would be a second spelling of "no data"
  // beside the absent key the contract declares, and a client reading Object.keys(families) would
  // count a family this Órgano does not have.
  @Test
  void omits_family_with_no_data_instead_of_sending_it_as_null() throws IOException {
    OrganoMemberResponse response = OrganoMemberResponse.of(sergas(), new FamiliesResponse(null));

    assertThat(serialised(response))
        .extracting("families", MAP)
        .doesNotContainKey("contratos-menores");
  }

  // Under the family's own slug, spelled as the client's child-route segment — the whole point of
  // keying on it is that no lookup table sits between this response and the router.
  @Test
  void serialises_held_family_under_its_route_segment() throws IOException {
    OrganoMemberResponse response =
        OrganoMemberResponse.of(sergas(), familiesWithContratosMenores());

    assertThat(serialised(response))
        .extracting("families", MAP)
        .containsKey("contratos-menores");
  }

  // The catalogue row's fields are absent by construction here rather than by omission: the page
  // renders neither, and folding them in would make this a second way to read the catalogue.
  @Test
  void withholds_the_catalogue_rows_state_and_placement() throws IOException {
    OrganoDeContratacion classified =
        new OrganoDeContratacion(ORGANO_ID, "test-sergas", "Servizo Galego de Saúde", true, true,
            new TermoId(UUID.randomUUID()));

    OrganoMemberResponse response =
        OrganoMemberResponse.of(classified, familiesWithContratosMenores());

    assertThat(serialised(response)).containsOnlyKeys("id", "name", "families");
  }

  @Test
  void refuses_organo_that_was_never_persisted() {
    OrganoDeContratacion unsaved =
        new OrganoDeContratacion("test-sergas", "Servizo Galego de Saúde");

    assertThatThrownBy(() -> OrganoMemberResponse.of(unsaved, new FamiliesResponse(null)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must carry an id");
  }

  private static OrganoDeContratacion sergas() {
    return new OrganoDeContratacion(
        ORGANO_ID, "test-sergas", "Servizo Galego de Saúde", true, true, null);
  }

  private static FamiliesResponse familiesWithContratosMenores() {
    return new FamiliesResponse(
        ContratosMenoresSummaryResponse.of(
            new ContratosMenoresSection(
                List.of(YearSelection.of(2025), YearSelection.of(2024), YearSelection.of(2023)),
                false, true)));
  }

  /**
   * The payload as a client parses it. These assertions are about the keys the serializer emits,
   * which the record's own components cannot show — a component is set either way, and what
   * decides whether it reaches the wire is the inclusion rule applied to it.
   */
  private static Map<String, Object> serialised(OrganoMemberResponse response) throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    String json = objectMapper.writeValueAsString(response);

    return objectMapper.readValue(json, Argument.mapOf(String.class, Object.class));
  }
}
