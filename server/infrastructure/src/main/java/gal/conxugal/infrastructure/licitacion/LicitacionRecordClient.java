package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/**
 * The one call {@link ContratosDeGaliciaLicitacionRecordSourceAdapter} makes: the page a procedure
 * is published on. There is no JSON equivalent to reach for — {@code api/v1/licitaciones/{id}} and
 * four sibling shapes all answer {@code 404}, so the HTML page is the whole of the contract.
 *
 * <p>The body is taken raw because the page is ISO-8859-1 and the adapter decodes it itself.
 * Decoding it as UTF-8 corrupts every accented name and object, which in Galician is most of them,
 * and corrupts the labels the parse looks values up by along with them. As a {@code @Get} it is
 * retryable without declaring itself idempotent.
 *
 * <p>The response is returned whole rather than as its body alone: a declarative client reads
 * {@code 404} as an absent value and hands back {@code null} instead of throwing, so an endpoint
 * that moved would otherwise be indistinguishable from a procedure that published nothing.
 *
 * <p><strong>An unknown identifier does not answer {@code 404}.</strong> It answers {@code 200}
 * with the site's error page, so no status check can tell a missing procedure from a present one
 * and the judgement belongs to the parse. The status checks are still worth making — they catch
 * the source being down — but they are not what finds a record that is not there.
 *
 * <p>The {@code id} keys the transport settings — base URL and the connect and read timeouts —
 * under {@code micronaut.http.services.contratosdegalicia}, and is deliberately the same one the
 * contratos menores and Órganos clients bind, so a record walk and a catalogue import draw on one
 * rate budget rather than one each.
 */
@Client(id = "contratosdegalicia")
@ResilientClient
interface LicitacionRecordClient {

  String RECORD_PATH = "/licitacion";

  @Get(RECORD_PATH)
  HttpResponse<byte[]> record(@QueryValue("N") String publicationId);
}
