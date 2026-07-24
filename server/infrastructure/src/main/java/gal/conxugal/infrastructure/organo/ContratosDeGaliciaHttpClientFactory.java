package gal.conxugal.infrastructure.organo;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URI;

/** Builds the {@link HttpClient} bean the {@link ContratosDeGaliciaOrganoSourceAdapter} uses. */
@Factory
class ContratosDeGaliciaHttpClientFactory {

  static final String CLIENT_NAME = "contratosDeGalicia";

  @Singleton
  @Named(CLIENT_NAME)
  @Bean(preDestroy = "close")
  HttpClient contratosDeGaliciaHttpClient(ContratosDeGaliciaConfiguration configuration)
      throws MalformedURLException {
    return HttpClient.create(URI.create(configuration.baseUrl()).toURL());
  }
}
