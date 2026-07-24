package gal.conxugal.infrastructure.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContratosDeGaliciaOrganoSourceAdapterTest {

  private static final int MIN_EXPECTED_ORGANOS = 50;
  private static final String PLACEHOLDER =
      option("", "Seleccione o organismo que desexa consultar");

  @Mock private HttpClient httpClient;
  @Mock private BlockingHttpClient blockingHttpClient;
  @Mock private HttpResponse<byte[]> response;

  @Test
  void parses_organos_including_entries_with_and_without_an_acronym() {
    String optionsHtml =
        PLACEHOLDER
            + option("48", "Academia Galega de Seguridade Pública (AGASP)")
            + option(
                "397", "Agrupación Europea de Cooperación Territorial Galicia-Norte de Portugal")
            + paddingOptions(MIN_EXPECTED_ORGANOS);
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter.fetchAll();

    assertThat(entries)
        .contains(
            new OrganoSourceEntry("48", "Academia Galega de Seguridade Pública", "AGASP"),
            new OrganoSourceEntry(
                "397",
                "Agrupación Europea de Cooperación Territorial Galicia-Norte de Portugal",
                null));
  }

  @Test
  void skips_the_leading_placeholder_option() {
    String optionsHtml =
        PLACEHOLDER + option("48", "Academia Galega") + paddingOptions(MIN_EXPECTED_ORGANOS);
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter.fetchAll();

    assertThat(entries)
        .extracting(OrganoSourceEntry::name)
        .doesNotContain("Seleccione o organismo que desexa consultar");
  }

  @Test
  void decodes_accented_characters_from_the_source_encoding_without_mojibake() {
    String accentedName = "Consello Galego de Relacións Laborais e Función Pública";
    String optionsHtml =
        PLACEHOLDER + option("512", accentedName) + paddingOptions(MIN_EXPECTED_ORGANOS);
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter.fetchAll();

    assertThat(entries)
        .extracting(OrganoSourceEntry::sourceKey, OrganoSourceEntry::name)
        .contains(org.assertj.core.groups.Tuple.tuple("512", accentedName));
  }

  @Test
  void non_leading_blank_value_option_falls_back_to_normalised_name_source_key() {
    String optionsHtml =
        PLACEHOLDER + option("", "Órgano De Proba") + paddingOptions(MIN_EXPECTED_ORGANOS);
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter.fetchAll();

    assertThat(entries)
        .filteredOn(entry -> "Órgano De Proba".equals(entry.name()))
        .extracting(OrganoSourceEntry::sourceKey)
        .containsExactly("organo de proba");
  }

  @Test
  void distinct_names_normalise_to_different_source_keys_when_no_id_is_present() {
    String optionsHtml =
        PLACEHOLDER
            + option("", "Consorcio Uno")
            + option("", "Consorcio Dous")
            + paddingOptions(MIN_EXPECTED_ORGANOS);
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter.fetchAll();

    String keyUno =
        entries.stream()
            .filter(entry -> "Consorcio Uno".equals(entry.name()))
            .findFirst()
            .orElseThrow()
            .sourceKey();
    String keyDous =
        entries.stream()
            .filter(entry -> "Consorcio Dous".equals(entry.name()))
            .findFirst()
            .orElseThrow()
            .sourceKey();
    assertThat(keyUno).isNotEqualTo(keyDous);
  }

  @Test
  void throws_when_the_source_responds_with_an_error_status() {
    when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
    when(blockingHttpClient.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.notFound()));
    ContratosDeGaliciaOrganoSourceAdapter adapter =
        new ContratosDeGaliciaOrganoSourceAdapter(httpClient);

    assertThatThrownBy(adapter::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  @Test
  void throws_when_the_source_is_unreachable() {
    when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
    when(blockingHttpClient.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenThrow(new HttpClientException("Connect Error: Connection refused"));
    ContratosDeGaliciaOrganoSourceAdapter adapter =
        new ContratosDeGaliciaOrganoSourceAdapter(httpClient);

    assertThatThrownBy(adapter::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientException.class);
  }

  @Test
  void throws_when_the_organoa_select_is_not_on_the_page() {
    ContratosDeGaliciaOrganoSourceAdapter adapter =
        adapterReturning("<html><body><p>unexpected page</p></body></html>");

    assertThatThrownBy(adapter::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_parsed_list_is_implausibly_small() {
    String optionsHtml = PLACEHOLDER + option("48", "Academia Galega");
    ContratosDeGaliciaOrganoSourceAdapter adapter = adapterReturning(portadaHtml(optionsHtml));

    assertThatThrownBy(adapter::fetchAll)
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  private ContratosDeGaliciaOrganoSourceAdapter adapterReturning(String html) {
    when(httpClient.toBlocking()).thenReturn(blockingHttpClient);
    when(blockingHttpClient.exchange(any(HttpRequest.class), eq(byte[].class)))
        .thenReturn(response);
    when(response.body()).thenReturn(html.getBytes(StandardCharsets.ISO_8859_1));
    return new ContratosDeGaliciaOrganoSourceAdapter(httpClient);
  }

  private static String portadaHtml(String optionsHtml) {
    return "<html><body><select id=\"organoA\">" + optionsHtml + "</select></body></html>";
  }

  private static String option(String value, String text) {
    return "<option value='" + value + "'>" + text + "</option>";
  }

  private static String paddingOptions(int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(option("pad-" + i, "Padding Body " + i));
    }
    return builder.toString();
  }
}
