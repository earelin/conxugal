package gal.conxugal.infrastructure.organo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import io.micronaut.http.HttpResponse;
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

  private static final int MIN_EXPECTED_ORGANOS =
      ContratosDeGaliciaOrganoSourceAdapter.MIN_EXPECTED_ORGANOS;
  private static final String PLACEHOLDER =
      option("", "Seleccione o organismo que desexa consultar");

  @Mock private PortadaClient portadaClient;

  @Test
  void parses_organos_from_the_organoa_select() {
    String optionsHtml =
        PLACEHOLDER
            + option("48", "Academia Galega de Seguridade Pública")
            + option(
                "397", "Agrupación Europea de Cooperación Territorial Galicia-Norte de Portugal")
            + paddingOptions(MIN_EXPECTED_ORGANOS);
    stubPortadaWith(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter().fetchAll();

    assertThat(entries)
        .contains(
            new OrganoSourceEntry("48", "Academia Galega de Seguridade Pública"),
            new OrganoSourceEntry(
                "397", "Agrupación Europea de Cooperación Territorial Galicia-Norte de Portugal"));
  }

  @Test
  void uses_the_option_value_as_the_source_key() {
    String optionsHtml =
        PLACEHOLDER + option("512", "Consorcio Galego") + paddingOptions(MIN_EXPECTED_ORGANOS);
    stubPortadaWith(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter().fetchAll();

    assertThat(entries)
        .filteredOn(entry -> "Consorcio Galego".equals(entry.name()))
        .extracting(OrganoSourceEntry::sourceKey)
        .containsExactly("512");
  }

  @Test
  void skips_the_leading_placeholder_option() {
    String optionsHtml =
        PLACEHOLDER + option("48", "Academia Galega") + paddingOptions(MIN_EXPECTED_ORGANOS);
    stubPortadaWith(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter().fetchAll();

    assertThat(entries)
        .extracting(OrganoSourceEntry::name)
        .doesNotContain("Seleccione o organismo que desexa consultar");
  }

  @Test
  void decodes_accented_characters_from_the_source_encoding_without_mojibake() {
    String accentedName = "Consello Galego de Relacións Laborais e Función Pública";
    String optionsHtml =
        PLACEHOLDER + option("512", accentedName) + paddingOptions(MIN_EXPECTED_ORGANOS);
    stubPortadaWith(portadaHtml(optionsHtml));

    List<OrganoSourceEntry> entries = adapter().fetchAll();

    assertThat(entries).contains(new OrganoSourceEntry("512", accentedName));
  }

  @Test
  void throws_when_the_source_responds_with_an_error_status() {
    when(portadaClient.portada())
        .thenThrow(new HttpClientResponseException("Server Error", HttpResponse.serverError()));

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientResponseException.class);
  }

  /**
   * The one error status a declarative client returns rather than throws, and the likeliest way
   * for the source to break: the portada moves and every run reports it as such.
   */
  @Test
  void throws_naming_the_status_when_the_portada_is_not_found() {
    when(portadaClient.portada()).thenReturn(HttpResponse.notFound());

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasMessageContaining("NOT_FOUND");
  }

  @Test
  void throws_when_the_source_is_unreachable() {
    when(portadaClient.portada())
        .thenThrow(new HttpClientException("Connect Error: Connection refused"));

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasCauseInstanceOf(HttpClientException.class);
  }

  @Test
  void throws_when_the_source_responds_with_an_empty_body() {
    when(portadaClient.portada()).thenReturn(HttpResponse.ok());

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_organoa_select_is_not_on_the_page() {
    stubPortadaWith("<html><body><p>unexpected page</p></body></html>");

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  @Test
  void throws_when_the_parsed_list_is_implausibly_small() {
    String optionsHtml = PLACEHOLDER + option("48", "Academia Galega");
    stubPortadaWith(portadaHtml(optionsHtml));

    assertThatThrownBy(() -> adapter().fetchAll())
        .isInstanceOf(OrganoSourceUnavailableException.class)
        .hasNoCause();
  }

  private ContratosDeGaliciaOrganoSourceAdapter adapter() {
    return new ContratosDeGaliciaOrganoSourceAdapter(portadaClient);
  }

  private void stubPortadaWith(String html) {
    when(portadaClient.portada())
        .thenReturn(HttpResponse.ok(html.getBytes(StandardCharsets.ISO_8859_1)));
  }

  private static String portadaHtml(String optionsHtml) {
    return "<html><body><select id=\"organoA\">%s</select></body></html>".formatted(optionsHtml);
  }

  private static String option(String value, String text) {
    return "<option value='%s'>%s</option>".formatted(value, text);
  }

  private static String paddingOptions(int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(option("pad-%d".formatted(i), "Padding Body %d".formatted(i)));
    }
    return builder.toString();
  }
}
