package gal.conxugal.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.exception.AcquirePermissionCancelledException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.exceptions.HttpClientException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decorates a raw {@link BlockingHttpClient} with retry, rate-limiting and circuit-breaking
 * policies, composed longhand as {@code Retry(CircuitBreaker(RateLimiter(exchange)))} — the
 * limiter innermost so every attempt, retries included, consumes a permit; the breaker between, so
 * an open circuit rejects a call before it burns rate budget; retry outermost, matching
 * Resilience4j's own aspect order.
 *
 * <p>No Resilience4j type crosses this class's boundary: {@link RequestNotPermitted}, {@link
 * CallNotPermittedException} and {@link AcquirePermissionCancelledException} are translated to
 * {@link HttpClientException}, the transport exception type callers already handle, and all three
 * are excluded from retry.
 */
public final class ResilientHttpClient {

  private static final String VERSION_RESOURCE = "conxugal-version.properties";
  private static final String UNKNOWN_VERSION = "unknown";

  static final String USER_AGENT_VALUE =
      "conxugal/%s (+https://github.com/earelin/conxugal)".formatted(readApplicationVersion());

  static final int BREAKER_MINIMUM_NUMBER_OF_CALLS =
      ResilientPolicies.BREAKER_MINIMUM_NUMBER_OF_CALLS;

  private final BlockingHttpClient delegate;
  private final RateLimiter rateLimiter;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public ResilientHttpClient(
      String name, BlockingHttpClient delegate, ResilientHttpClientSettings settings) {
    this(name, delegate, settings, Clock.systemUTC());
  }

  ResilientHttpClient(
      String name,
      BlockingHttpClient delegate,
      ResilientHttpClientSettings settings,
      Clock clock) {
    this.delegate = delegate;
    this.rateLimiter = RateLimiter.of(name, ResilientPolicies.rateLimiterConfig(settings));
    this.circuitBreaker =
        CircuitBreaker.of(name, ResilientPolicies.circuitBreakerConfig(settings));
    this.retry = Retry.of(name, ResilientPolicies.retryConfig(settings, clock));
  }

  /** GET/HEAD-default overload: retryable per the method, nothing else. */
  public <T> T exchange(
      MutableHttpRequest<?> request,
      Function<HttpResponse<byte[]>, T> responseMapper,
      Predicate<T> isAcceptable) {
    return exchange(request, responseMapper, isAcceptable, false);
  }

  /**
   * Full form: the caller declares idempotency explicitly for any non-GET/HEAD method.
   *
   * <p>{@code request} is mutated in place (its {@code User-Agent} header is set) and consumed by
   * this call; it must not be shared across concurrent {@code exchange} calls.
   */
  public <T> T exchange(
      MutableHttpRequest<?> request,
      Function<HttpResponse<byte[]>, T> responseMapper,
      Predicate<T> isAcceptable,
      boolean requestIsIdempotent) {
    request.getHeaders().set(HttpHeaders.USER_AGENT, USER_AGENT_VALUE);
    boolean retryable = isRetryableByMethod(request.getMethod()) || requestIsIdempotent;

    Supplier<T> exchangeAndMap =
        () -> {
          HttpResponse<byte[]> response = delegate.exchange(request, byte[].class);
          T mapped = responseMapper.apply(response);
          if (!isAcceptable.test(mapped)) {
            throw new UnacceptableResponseException(
                "Response for %s %s failed the caller's acceptability check"
                    .formatted(request.getMethod(), request.getPath()));
          }
          return mapped;
        };

    Supplier<T> guarded =
        CircuitBreaker.decorateSupplier(
            circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, exchangeAndMap));
    Supplier<T> composed = retryable ? Retry.decorateSupplier(retry, guarded) : guarded;

    try {
      return composed.get();
    } catch (RequestNotPermitted e) {
      throw new HttpClientException(
          "Rate limiter refused a permit for %s %s"
              .formatted(request.getMethod(), request.getPath()),
          e);
    } catch (CallNotPermittedException e) {
      throw new HttpClientException(
          "Circuit breaker is open for %s %s".formatted(request.getMethod(), request.getPath()),
          e);
    } catch (AcquirePermissionCancelledException e) {
      Thread.currentThread().interrupt();
      throw new HttpClientException(
          "Rate limiter wait was interrupted for %s %s"
              .formatted(request.getMethod(), request.getPath()),
          e);
    }
  }

  private static boolean isRetryableByMethod(HttpMethod method) {
    return method == HttpMethod.GET || method == HttpMethod.HEAD;
  }

  /**
   * The version resource is generated by this module's own build ({@code
   * generateVersionProperties} in {@code infrastructure/build.gradle.kts}), so it's present on
   * every classpath that includes {@code infrastructure}'s {@code main} output.
   */
  private static String readApplicationVersion() {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
      if (in == null) {
        return UNKNOWN_VERSION;
      }
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("version", UNKNOWN_VERSION);
    } catch (IOException e) {
      return UNKNOWN_VERSION;
    }
  }
}
