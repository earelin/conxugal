package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/**
 * The one call {@link ContratosDeGaliciaLicitacionListingSourceAdapter} makes: the JSON API the
 * Órgano's licitacións tab fetches its own table from. It is the cheap half of this family's
 * retrieval — the whole record lives on an HTML page reached one procedure at a time.
 *
 * <p>The response is JSON in UTF-8, which is Micronaut's own default, unlike the ISO-8859-1 HTML
 * of the per-procedure record. One source, two charsets.
 *
 * <p>The response is returned whole rather than as its body alone: a declarative client reads
 * {@code 404} as an absent value and hands back {@code null} instead of throwing, so an endpoint
 * that moved would otherwise be indistinguishable from an Órgano that published nothing.
 *
 * <p>The {@code id} keys the transport settings — base URL and the connect and read timeouts —
 * under {@code micronaut.http.services.contratosdegalicia}, and is deliberately the same one the
 * contratos menores and Órganos clients bind: the resilience policies are singletons per source,
 * so sharing the id is what makes a licitacións walk and a contratos menores import draw on one
 * rate budget rather than each holding a full one.
 *
 * <p><strong>{@link #COLUMNS} is not optional and there is no method without it.</strong> The
 * server resolves the order column by name, so a request carrying {@code order[0][column]} alone
 * answers {@code 500} and only one carrying every {@code columns[i][name]} answers {@code 200}. It
 * belongs to the endpoint's contract rather than to any caller, which is why it is written into
 * the path here instead of being passed in — an abbreviated request works against a lenient stub
 * and fails against the source, and that defect has no way in from this declaration.
 *
 * <p>{@code idioma} pins the state labels to the vocabulary the source contract measured; two of
 * its ten codes already share a label, and a response in another language would give the same
 * codes different ones.
 *
 * <p>{@code draw} is the table widget's own echo counter. The source requires it and nothing here
 * reads it back, so it is sent as a constant.
 */
@Client(id = "contratosdegalicia")
@ResilientClient
interface LicitacionsClient {

  String TABLE_PATH = "/api/v1/organismos/{organismo}/licitaciones/table";

  String COLUMNS =
      "?columns[0][name]=id&columns[1][name]=objeto&columns[2][name]=importe"
          + "&columns[3][name]=estado&columns[4][name]=publicado&columns[5][name]=modificado"
          + "&idioma=gl";

  /** Where {@code id} sits in {@link #COLUMNS}, which is what {@code order[0][column]} names. */
  int ID_COLUMN = 0;

  @Get(TABLE_PATH + COLUMNS)
  HttpResponse<LicitacionsTable> table(
      @PathVariable String organismo,
      @QueryValue("start") int start,
      @QueryValue("length") int length,
      @QueryValue("draw") int draw,
      @QueryValue("order[0][column]") int orderColumn,
      @QueryValue("order[0][dir]") String orderDirection);
}
