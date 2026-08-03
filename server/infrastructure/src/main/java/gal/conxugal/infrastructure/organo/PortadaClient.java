package gal.conxugal.infrastructure.organo;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

/**
 * The one call {@link ContratosDeGaliciaOrganoSourceAdapter} makes: contratosdegalicia.gal's front
 * page, whose static HTML embeds the Órganos list.
 *
 * <p>The body is taken raw because the source is ISO-8859-1 and the adapter decodes it itself. As a
 * {@code @Get} it is retryable without declaring itself idempotent.
 *
 * <p>The response is returned whole rather than as its body alone: a declarative client reads
 * {@code 404} as an absent value and hands back {@code null} instead of throwing, so a portada that
 * moved would otherwise be indistinguishable from an empty one. Every other error status still
 * arrives as an exception.
 *
 * <p>The {@code id} keys the transport settings — base URL and the connect and read timeouts —
 * under {@code micronaut.http.services.contratosdegalicia}.
 */
@Client(id = "contratosdegalicia")
@ResilientClient
interface PortadaClient {

  String PORTADA_PATH = "/portada.jsp";

  @Get(PORTADA_PATH)
  HttpResponse<byte[]> portada();
}
