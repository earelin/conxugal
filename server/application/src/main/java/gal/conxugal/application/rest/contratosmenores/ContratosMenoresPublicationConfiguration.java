package gal.conxugal.application.rest.contratosmenores;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Where a contrato menor is published at the official source, so a row can link to the original
 * and be checked against it. The address is derivable from the identifier the row already carries,
 * which is why no column holds it.
 *
 * <p><b>It is deliberately not the import client's base URL.</b>
 * {@code micronaut.http.services.contratosdegalicia.url} is what the importer scrapes, and
 * {@code server/docker-compose.yml} points it at the WireMock stub — so borrowing it would render
 * every public link as {@code http://contratosdegalicia:8080/…} in development, in preview and in
 * the end-to-end suite. The host a reader follows and the host the importer reads are two facts
 * that coincide only in production, so they are two properties.
 *
 * <p>Composed on the server rather than in the browser: the source's URL shape then lives in one
 * place, where a test can assert it, instead of an external host being hard-coded in the SPA.
 */
@ConfigurationProperties(ContratosMenoresPublicationConfiguration.PREFIX)
public record ContratosMenoresPublicationConfiguration(
    @Bindable(defaultValue = "https://www.contratosdegalicia.gal") String baseUrl) {

  static final String PREFIX = "conxugal.contratos-menores.publication";

  /**
   * Refuses a base nobody could follow, and drops trailing slashes so that configuring one does
   * not produce a link carrying two.
   */
  public ContratosMenoresPublicationConfiguration {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("base-url must be set");
    }
    baseUrl = baseUrl.strip().replaceAll("/+$", "");
    if (baseUrl.isEmpty()) {
      throw new IllegalArgumentException("base-url must be more than slashes");
    }
  }

  /** The publication of the contract the source published under {@code sourceId}. */
  public String urlOf(long sourceId) {
    return "%s/licitacion?N=%d".formatted(baseUrl, sourceId);
  }
}
