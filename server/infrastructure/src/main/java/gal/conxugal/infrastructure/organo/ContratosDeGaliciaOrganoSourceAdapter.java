package gal.conxugal.infrastructure.organo;

import gal.conxugal.domain.organo.OrganoSource;
import gal.conxugal.domain.organo.OrganoSourceEntry;
import gal.conxugal.domain.organo.OrganoSourceUnavailableException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * {@link OrganoSource} adapter for contratosdegalicia.gal: the published Órganos list is embedded
 * directly in the (ISO-8859-1) static HTML of {@code /portada.jsp} as a {@code <select
 * id="organoA">}, one {@code <option value='sourceId'>Name</option>} per body — no separate
 * dynamic endpoint exists for it.
 */
@Singleton
public class ContratosDeGaliciaOrganoSourceAdapter implements OrganoSource {

  private static final String PORTADA_PATH = "/portada.jsp";
  private static final String SELECT_SELECTOR = "select#organoA";

  /**
   * A generous floor under the real catalogue (~429 bodies at the time of writing): fewer than
   * this signals a source glitch or truncated response, not a genuinely shrunk catalogue, so it
   * fails the run instead of letting a reconciliation use case mass-deactivate real bodies.
   */
  private static final int MIN_EXPECTED_ORGANOS = 50;

  private final BlockingHttpClient httpClient;

  @Inject
  public ContratosDeGaliciaOrganoSourceAdapter(
      @Named(ContratosDeGaliciaHttpClientFactory.CLIENT_NAME) HttpClient httpClient) {
    this.httpClient = httpClient.toBlocking();
  }

  @Override
  public List<OrganoSourceEntry> fetchAll() {
    byte[] body = fetchPortada();
    Document document = parseHtml(body);

    Element select = document.selectFirst(SELECT_SELECTOR);
    if (select == null) {
      throw new OrganoSourceUnavailableException(
          "Órganos list not found on the source page: no " + SELECT_SELECTOR + " element");
    }

    List<OrganoSourceEntry> entries = parseEntries(select);
    if (entries.size() < MIN_EXPECTED_ORGANOS) {
      throw new OrganoSourceUnavailableException(
          "Órganos list is implausibly small: got "
              + entries.size()
              + " entries, expected at least "
              + MIN_EXPECTED_ORGANOS);
    }
    return entries;
  }

  private static Document parseHtml(byte[] body) {
    try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
      return Jsoup.parse(in, StandardCharsets.ISO_8859_1.name(), PORTADA_PATH);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private byte[] fetchPortada() {
    try {
      HttpResponse<byte[]> response =
          httpClient.exchange(HttpRequest.GET(PORTADA_PATH), byte[].class);
      byte[] body = response.body();
      if (body == null) {
        throw new OrganoSourceUnavailableException("Source returned an empty response body");
      }
      return body;
    } catch (HttpClientResponseException e) {
      throw new OrganoSourceUnavailableException(
          "Source responded with status " + e.getStatus(), e);
    } catch (HttpClientException e) {
      throw new OrganoSourceUnavailableException("Source is unreachable: " + e.getMessage(), e);
    }
  }

  /**
   * The first option of the {@code <select>} is the source's own "Seleccione o organismo..."
   * placeholder prompt (a blank {@code value}), not a real entry, and is skipped; every other
   * option's {@code value} is the source's own stable id for that Órgano.
   */
  private static List<OrganoSourceEntry> parseEntries(Element select) {
    List<Element> options = select.select("option");
    List<OrganoSourceEntry> entries = new ArrayList<>();
    for (int i = 0; i < options.size(); i++) {
      Element option = options.get(i);
      String sourceKey = option.attr("value").trim();
      if (i == 0 && sourceKey.isEmpty()) {
        continue;
      }
      entries.add(new OrganoSourceEntry(sourceKey, option.text().trim()));
    }
    return entries;
  }
}
