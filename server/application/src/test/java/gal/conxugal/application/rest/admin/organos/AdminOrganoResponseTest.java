package gal.conxugal.application.rest.admin.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.conxugal.domain.contrato.ContratosMenoresImportStatus;
import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class AdminOrganoResponseTest {

  private static final OrganoId ORGANO_ID = new OrganoId(UUID.randomUUID());
  private static final TermoId TERMO_ID = new TermoId(UUID.randomUUID());

  @Test
  void carries_the_placement_alongside_the_import_mark() {
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "sanidade", "Consellería de Sanidade", true, true,
            TERMO_ID),
        ContratosMenoresImportStatus.INCOMPLETE);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.id()).isEqualTo(ORGANO_ID.value());
      softly.assertThat(response.name()).isEqualTo("Consellería de Sanidade");
      softly.assertThat(response.active()).isTrue();
      softly.assertThat(response.termoId()).isEqualTo(TERMO_ID.value());
      softly.assertThat(response.importable()).isTrue();
      softly.assertThat(response.importState())
          .isEqualTo(ContratosMenoresImportStatus.INCOMPLETE);
    });
  }

  // The mark and the state are independent facts, and a marked Órgano whose import has not run
  // is the pair that proves it: inferring one from the other would render it as up to date.
  @Test
  void carries_the_import_state_independently_of_the_mark() {
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, true, null),
        ContratosMenoresImportStatus.NEVER_STARTED);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.importable()).isTrue();
      softly.assertThat(response.importState())
          .isEqualTo(ContratosMenoresImportStatus.NEVER_STARTED);
    });
  }

  @Test
  void serialises_the_import_state_by_name() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, true, null),
        ContratosMenoresImportStatus.COMPLETE);

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap)).containsEntry("importState", "COMPLETE");
  }

  @Test
  void reads_an_organo_that_was_never_marked_as_unmarked() {
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, false, null),
        ContratosMenoresImportStatus.NEVER_STARTED);

    assertThat(response.importable()).isFalse();
  }

  @Test
  void maps_unclassified_organo_to_null_termo_id() {
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, false, null),
        ContratosMenoresImportStatus.NEVER_STARTED);

    assertThat(response.termoId()).isNull();
  }

  // A marked Órgano that has gone inactive keeps both facts: the catalogue import clears
  // neither the mark nor the row, and an administrator has to see the pair to understand why
  // an Órgano is marked yet not being imported.
  @Test
  void carries_the_mark_of_an_inactive_organo() {
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", false, true, null),
        ContratosMenoresImportStatus.NEVER_STARTED);

    SoftAssertions.assertSoftly(softly -> {
      softly.assertThat(response.active()).isFalse();
      softly.assertThat(response.importable()).isTrue();
    });
  }

  // The serializer's default inclusion drops a null, which would take termoId out of the
  // payload where the contract declares it required. Asserting the serialized key rather than
  // the record's field is what makes @JsonInclude(ALWAYS) load-bearing here too.
  @Test
  void serialises_unclassified_organo_with_an_explicit_null_termo_id() throws IOException {
    ObjectMapper objectMapper = ObjectMapper.getDefault();
    AdminOrganoResponse response = AdminOrganoResponse.of(
        new OrganoDeContratacion(ORGANO_ID, "mar", "Consellería do Mar", true, false, null),
        ContratosMenoresImportStatus.NEVER_STARTED);

    String json = objectMapper.writeValueAsString(response);

    Argument<Map<String, Object>> asMap = Argument.mapOf(String.class, Object.class);
    assertThat(objectMapper.readValue(json, asMap)).containsEntry("termoId", null);
  }

  @Test
  void refuses_organo_that_was_never_persisted() {
    OrganoDeContratacion unsaved = new OrganoDeContratacion("mar", "Consellería do Mar");

    assertThatThrownBy(
        () -> AdminOrganoResponse.of(unsaved, ContratosMenoresImportStatus.NEVER_STARTED))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("must carry an id");
  }
}
