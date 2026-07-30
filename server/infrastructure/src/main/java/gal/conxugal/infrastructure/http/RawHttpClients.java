package gal.conxugal.infrastructure.http;

import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClient;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the raw {@link HttpClient} a {@link ResilientHttpClient} decorates, from the same
 * settings record. Keeping construction here is what makes the base URL and the two timeouts
 * binding rather than advisory: a source that forgot its connect timeout would inherit OS
 * behaviour, multiplied by the retry attempt count, which is worst in exactly the blackholed-host
 * case the policies exist to survive.
 */
public final class RawHttpClients {

  private RawHttpClients() {}

  /**
   * The returned client is {@link java.io.Closeable} and owns a connection pool; the caller keeps
   * it for the lifetime of the source it talks to.
   */
  public static HttpClient forSource(ResilientHttpClientSettings settings) {
    ResilientPolicies.validate(settings);
    DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
    configuration.setConnectTimeout(settings.connectTimeout());
    configuration.setReadTimeout(settings.readTimeout());
    return HttpClient.create(baseUrl(settings.baseUrl()), configuration);
  }

  private static java.net.URL baseUrl(String baseUrl) {
    try {
      return new URI(baseUrl).toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      throw new IllegalArgumentException("baseUrl is not a valid URL: %s".formatted(baseUrl), e);
    }
  }
}
