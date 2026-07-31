package gal.conxugal.infrastructure.http;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.exception.AcquirePermissionCancelledException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.client.exceptions.HttpClientException;
import jakarta.inject.Singleton;
import java.util.function.Supplier;

/**
 * Runs a {@link ResilientClient} method under the three Resilience4j policies, composed longhand as
 * {@code Retry(CircuitBreaker(RateLimiter(call)))} — the limiter innermost so every attempt,
 * retries included, consumes a permit; the breaker between, so an open circuit rejects a call
 * before it burns rate budget; retry outermost, matching Resilience4j's own aspect order.
 *
 * <p>No Resilience4j type crosses this interceptor: {@link RequestNotPermitted}, {@link
 * CallNotPermittedException} and {@link AcquirePermissionCancelledException} become {@link
 * HttpClientException}, the transport type adapters already catch, and all three are excluded from
 * retry — retrying a refused permit or an open circuit turns a deliberate fail-fast into a
 * fail-slow.
 *
 * <p>The policy beans are injected unqualified because one external source exists. A second source
 * needs its own budget rather than a share of this one, so it also needs qualifiers here; nothing
 * about that is decided yet.
 */
@Singleton
@InterceptorBean(ResilientClient.class)
final class ResilientClientInterceptor implements MethodInterceptor<Object, Object> {

  private final Retry retry;
  private final CircuitBreaker circuitBreaker;
  private final RateLimiter rateLimiter;

  ResilientClientInterceptor(Retry retry, CircuitBreaker circuitBreaker, RateLimiter rateLimiter) {
    this.retry = retry;
    this.circuitBreaker = circuitBreaker;
    this.rateLimiter = rateLimiter;
  }

  @Override
  public Object intercept(MethodInvocationContext<Object, Object> context) {
    requireSynchronous(context);

    // proceed(this), not proceed(): the chain advances an index as it runs, so a plain proceed()
    // can only be called once — a second attempt would run off the end of the chain and fail with
    // UnimplementedAdviceException instead of reaching the client. Passing this interceptor rewinds
    // the chain to just after it, which is what makes an attempt repeatable.
    Supplier<Object> attempt = () -> context.proceed(this);
    Supplier<Object> guarded =
        CircuitBreaker.decorateSupplier(
            circuitBreaker, RateLimiter.decorateSupplier(rateLimiter, attempt));
    Supplier<Object> composed =
        isRetryable(context) ? Retry.decorateSupplier(retry, guarded) : guarded;

    try {
      return composed.get();
    } catch (RequestNotPermitted e) {
      throw new HttpClientException(
          "Rate limiter refused a permit for %s".formatted(describe(context)), e);
    } catch (CallNotPermittedException e) {
      throw new HttpClientException(
          "Circuit breaker is open for %s".formatted(describe(context)), e);
    } catch (AcquirePermissionCancelledException e) {
      Thread.currentThread().interrupt();
      throw new HttpClientException(
          "Rate limiter wait was interrupted for %s".formatted(describe(context)), e);
    }
  }

  private static boolean isRetryable(MethodInvocationContext<Object, Object> context) {
    return context.hasAnnotation(Get.class)
        || context.hasAnnotation(Head.class)
        || context.isTrue(ResilientClient.class, "idempotent");
  }

  private static void requireSynchronous(MethodInvocationContext<Object, Object> context) {
    if (context.getReturnType().isAsyncOrReactive()) {
      throw new UnsupportedOperationException(
          ("%s returns a reactive or asynchronous type, which this policy cannot judge: it would "
                  + "time the construction of the publisher rather than the exchange. Declare the "
                  + "method to return its value directly.")
              .formatted(describe(context)));
    }
  }

  private static String describe(MethodInvocationContext<Object, Object> context) {
    return "%s.%s".formatted(context.getDeclaringType().getSimpleName(), context.getMethodName());
  }
}
