package gal.conxugal.infrastructure.contrato;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/**
 * The one call {@link ContratosDeGaliciaContratoMenorSourceAdapter} makes: the JSON API the
 * <em>Perfil do contratante</em> page fetches its own contratos menores table from. There is no
 * HTML to parse and no JavaScript to run — the page holds no contract data of its own.
 *
 * <p>The response is JSON in UTF-8, which is Micronaut's own default, unlike the ISO-8859-1 HTML
 * the Órganos adapter reads and decodes itself. The two adapters share a source, not a charset.
 *
 * <p>The response is returned whole rather than as its body alone: a declarative client reads
 * {@code 404} as an absent value and hands back {@code null} instead of throwing, so an endpoint
 * that moved would otherwise be indistinguishable from an Órgano that published nothing.
 *
 * <p>The {@code id} keys the transport settings — base URL and the connect and read timeouts —
 * under {@code micronaut.http.services.contratosdegalicia}, and is deliberately the same one the
 * Órganos client binds: the resilience policies are singletons per source, so sharing the id is
 * what makes the two share one rate budget rather than each holding a full one.
 *
 * <p>{@code draw} is the table widget's own echo counter. The source requires it and nothing here
 * reads it back, so it is sent as a constant.
 */
@Client(id = "contratosdegalicia")
@ResilientClient
interface ContratosMenoresClient {

  String TABLE_PATH = "/api/v1/organismos/{organismo}/contratosmenores/table";

  @Get(TABLE_PATH)
  HttpResponse<ContratosMenoresTable> table(
      @PathVariable String organismo,
      @QueryValue("datestart") String dateStart,
      @QueryValue("dateend") String dateEnd,
      @QueryValue("start") int start,
      @QueryValue("length") int length,
      @QueryValue("draw") int draw);
}
