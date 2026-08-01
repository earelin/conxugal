package gal.conxugal.infrastructure.organo;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

/**
 * The one call {@link ContratosDeGaliciaOrganoSourceAdapter} makes: contratosdegalicia.gal's front
 * page, whose static HTML embeds the Órganos list.
 *
 * <p>The body is taken raw because the source is ISO-8859-1 and the adapter decodes it itself. As a
 * {@code @Get} it is retryable without declaring itself idempotent.
 *
 * <p>The {@code id} keys the transport settings — base URL and the connect and read timeouts —
 * under {@code micronaut.http.services.contratosdegalicia}.
 */
@Client(id = "contratosdegalicia")
@ResilientClient
interface PortadaClient {

  String PORTADA_PATH = "/portada.jsp";

  @Get(PORTADA_PATH)
  byte[] portada();
}
