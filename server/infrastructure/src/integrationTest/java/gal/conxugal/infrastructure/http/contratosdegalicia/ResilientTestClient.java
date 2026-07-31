package gal.conxugal.infrastructure.http.contratosdegalicia;

import gal.conxugal.infrastructure.http.ResilientClient;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/**
 * Stands in for the real source clients, which land in TASK-0008. It exists so the advice can be
 * exercised against a genuine Micronaut-generated client rather than a mocked invocation context —
 * the one thing a unit test of the interceptor cannot check is whether Micronaut applies it at all.
 */
@Client(id = ContratosDeGaliciaResilienceFactory.NAME)
@ResilientClient
interface ResilientTestClient {

  @Get("/organos")
  byte[] organos();

  @Post("/search")
  byte[] search(@Body String query);

  @Post("/search")
  @ResilientClient(idempotent = true)
  byte[] searchDeclaredIdempotent(@Body String query);
}
