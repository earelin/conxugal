package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.licitacion.LicitacionRecord;
import gal.conxugal.domain.licitacion.LicitacionRecordSource;
import gal.conxugal.domain.licitacion.LicitacionRecordUnavailableException;
import gal.conxugal.domain.licitacion.PublicationId;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * {@link LicitacionRecordSource} adapter for contratosdegalicia.gal: a procedure is published as
 * one HTML page, reached by its own identifier, and everything this feature reads is in that one
 * response — the lotes, bidder and resolution tables are pre-rendered into modals rather than
 * fetched on demand.
 *
 * <p>This class owns the exchange and whether an answer arrived at all.
 * {@link LicitacionRecordDocument} owns whether the answer is a record and what it says, which is
 * where the values the source publishes are narrowed to the ones the port answers with.
 *
 * <p><strong>A failure names the status as a number, not as an {@code HttpStatus}.</strong>
 * {@code HttpResponse.getStatus()} resolves the code against Micronaut's own constants and throws
 * {@link IllegalArgumentException} for anything outside them — a CDN's 520 to 526, say. Building
 * the failure message that way would replace the port's declared failure with an unrelated
 * unchecked one at exactly the moment the source is misbehaving, and this port issues one request
 * per stored procedure rather than one per import.
 *
 * <p><strong>The page is decoded as ISO-8859-1</strong>, which is what it declares — and unlike its
 * own JSON listing, which is UTF-8. One source, two charsets. Decoding it as UTF-8 corrupts every
 * accented object and name, and corrupts the labels the parse looks values up by along with them,
 * so the failure is not confined to the text it mangles. The bytes are taken raw from the client
 * for exactly this reason, on the shipped Órganos adapter's precedent.
 */
@Singleton
public class ContratosDeGaliciaLicitacionRecordSourceAdapter implements LicitacionRecordSource {

  private final LicitacionRecordClient licitacionRecordClient;

  public ContratosDeGaliciaLicitacionRecordSourceAdapter(
      LicitacionRecordClient licitacionRecordClient) {
    this.licitacionRecordClient = licitacionRecordClient;
  }

  @Override
  public LicitacionRecord fetchRecord(PublicationId publicationId) {
    Objects.requireNonNull(publicationId, "publicationId must not be null");

    byte[] body = fetchRecordPage(publicationId);
    return LicitacionRecordDocument.read(publicationId, parseHtml(publicationId, body));
  }

  private byte[] fetchRecordPage(PublicationId publicationId) {
    try {
      HttpResponse<byte[]> response = licitacionRecordClient.record(publicationId.value());
      // The one error status that arrives as a response rather than an exception: a declarative
      // client reads 404 as an absent value, so judging it is the adapter's to do.
      if (response.code() >= HttpStatus.BAD_REQUEST.getCode()) {
        throw new LicitacionRecordUnavailableException(
            "Source responded with status %d for %s"
                .formatted(response.code(), publicationId.value()));
      }
      byte[] body = response.body();
      if (body == null) {
        throw new LicitacionRecordUnavailableException(
            "Source returned an empty response body for %s".formatted(publicationId.value()));
      }
      return body;
    } catch (HttpClientResponseException e) {
      throw new LicitacionRecordUnavailableException(
          "Source responded with status %d for %s"
              .formatted(e.getResponse().code(), publicationId.value()),
          e);
    } catch (HttpClientException e) {
      throw new LicitacionRecordUnavailableException(
          "Source is unreachable: %s".formatted(e.getMessage()), e);
    }
  }

  private static Document parseHtml(PublicationId publicationId, byte[] body) {
    try (ByteArrayInputStream in = new ByteArrayInputStream(body)) {
      return Jsoup.parse(
          in, StandardCharsets.ISO_8859_1.name(), LicitacionRecordClient.RECORD_PATH);
    } catch (IOException e) {
      throw new LicitacionRecordUnavailableException(
          "Source response for %s could not be parsed".formatted(publicationId.value()), e);
    }
  }
}
