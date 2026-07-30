package gal.conxugal.infrastructure.http;

import gal.conxugal.infrastructure.version.ApplicationVersion;
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
import java.time.Clock;
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
 *
 * <p><strong>Exactly one instance per source.</strong> Each instance owns its own rate limiter and
 * circuit breaker rather than resolving them from a shared registry, so the {@code name} argument
 * labels the policies for diagnostics but confers no identity. Two instances built for the same
 * source each get a full rate budget and pace independently, which silently doubles the request
 * rate that source sees; give one instance to every adapter that calls it.
 */
public final class ResilientHttpClient {

  static final String USER_AGENT_VALUE =
      "conxugal/%s (+https://github.com/earelin/conxugal)".formatted(ApplicationVersion.read());

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
    ResilientPolicies.validate(settings);
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
          T mapped =
              guardCallerCode(request, "Response mapper", () -> responseMapper.apply(response));
          if (!guardCallerCode(request, "Acceptability check", () -> isAcceptable.test(mapped))) {
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

  /**
   * The caller's mapper and acceptability check run inside the breaker, so a defect in either
   * would otherwise be recorded against the source's health. Catching broadly is the point: any
   * failure of caller-supplied code is fenced off as a {@link ResponseMappingException}, which the
   * breaker ignores.
   */
  @SuppressWarnings("PMD.AvoidCatchingGenericException")
  private static <R> R guardCallerCode(
      MutableHttpRequest<?> request, String description, Supplier<R> callerCode) {
    try {
      return callerCode.get();
    } catch (RuntimeException e) {
      throw asTransportOrMappingFailure(request, description, e);
    }
  }

  /**
   * A transport failure the caller's own code propagated is a real signal about the source, so it
   * travels on unchanged; anything else is the caller's defect.
   */
  private static RuntimeException asTransportOrMappingFailure(
      MutableHttpRequest<?> request, String description, RuntimeException cause) {
    if (cause instanceof HttpClientException transportFailure) {
      return transportFailure;
    }
    return new ResponseMappingException(
        "%s for %s %s failed".formatted(description, request.getMethod(), request.getPath()),
        cause);
  }

  private static boolean isRetryableByMethod(HttpMethod method) {
    return method == HttpMethod.GET || method == HttpMethod.HEAD;
  }
}
