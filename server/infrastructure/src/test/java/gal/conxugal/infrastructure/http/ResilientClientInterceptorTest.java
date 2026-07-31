package gal.conxugal.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResilientClientInterceptorTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final Duration NO_WAIT = Duration.ofMillis(1);
  private static final Duration RETRY_AFTER_MAXIMUM_WAIT = Duration.ofSeconds(30);

  @Mock private MethodInvocationContext<Object, Object> context;
  @Mock private ReturnType<Object> returnType;

  private final Retry retry =
      Retry.of(
          "test",
          ResilientClientPolicies.retry(
              MAX_ATTEMPTS, NO_WAIT, RETRY_AFTER_MAXIMUM_WAIT, Clock.systemUTC()));
  private final CircuitBreaker circuitBreaker =
      CircuitBreaker.of("test", ResilientClientPolicies.circuitBreaker(50, Duration.ofMinutes(1)));
  private final RateLimiter rateLimiter =
      RateLimiter.of("test", ResilientClientPolicies.rateLimiter(NO_WAIT, Duration.ofSeconds(1)));

  private ResilientClientInterceptor interceptor() {
    return new ResilientClientInterceptor(retry, circuitBreaker, rateLimiter);
  }

  @Test
  void retries_transient_failures_until_one_succeeds() {
    AtomicInteger attempts = givenGetFailingWith(status(503), 1, "recovered");

    Object result = interceptor().intercept(context);

    assertThat(result).isEqualTo("recovered");
    assertThat(attempts).hasValue(2);
  }

  @Test
  void gives_up_once_the_configured_attempts_are_exhausted() {
    AtomicInteger attempts = givenGetFailingWith(status(503), Integer.MAX_VALUE, "unreachable");

    assertThatThrownBy(() -> interceptor().intercept(context))
        .isInstanceOf(HttpClientResponseException.class);

    assertThat(attempts).hasValue(MAX_ATTEMPTS);
  }

  @Test
  void does_not_retry_permanent_statuses() {
    AtomicInteger attempts = givenGetFailingWith(status(404), Integer.MAX_VALUE, "unreachable");

    assertThatThrownBy(() -> interceptor().intercept(context))
        .isInstanceOf(HttpClientResponseException.class);

    assertThat(attempts).hasValue(1);
  }

  @Test
  void does_not_retry_post_without_idempotent_declaration() {
    givenSynchronousMethod();
    AtomicInteger attempts = givenProceedFailingWith(status(503), Integer.MAX_VALUE, "unused");

    assertThatThrownBy(() -> interceptor().intercept(context))
        .isInstanceOf(HttpClientResponseException.class);

    assertThat(attempts).hasValue(1);
  }

  @Test
  void retries_post_declared_idempotent() {
    givenSynchronousMethod();
    when(context.isTrue(ResilientClient.class, "idempotent")).thenReturn(true);
    AtomicInteger attempts = givenProceedFailingWith(status(503), 1, "recovered");

    Object result = interceptor().intercept(context);

    assertThat(result).isEqualTo("recovered");
    assertThat(attempts).hasValue(2);
  }

  @Test
  void translates_refused_permit_into_the_transport_exception_type() {
    RateLimiter exhausted =
        RateLimiter.of(
            "exhausted",
            RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)
                .build());
    exhausted.acquirePermission();
    givenSynchronousMethod();

    ResilientClientInterceptor interceptor =
        new ResilientClientInterceptor(retry, circuitBreaker, exhausted);

    assertThatThrownBy(() -> interceptor.intercept(context))
        .isInstanceOf(HttpClientException.class)
        .hasMessageContaining("Rate limiter refused a permit");
  }

  @Test
  void translates_an_open_circuit_into_the_transport_exception_type() {
    CircuitBreaker open =
        CircuitBreaker.of(
            "open", ResilientClientPolicies.circuitBreaker(50, Duration.ofMinutes(1)));
    open.transitionToOpenState();
    givenSynchronousMethod();

    assertThatThrownBy(
            () -> new ResilientClientInterceptor(retry, open, rateLimiter).intercept(context))
        .isInstanceOf(HttpClientException.class)
        .hasMessageContaining("Circuit breaker is open")
        .hasCauseInstanceOf(CallNotPermittedException.class);
  }

  @Test
  void aborts_when_retry_after_exceeds_the_configured_maximum_wait() {
    HttpResponse<?> response = status(503);
    lenient()
        .when(response.header(HttpHeaders.RETRY_AFTER))
        .thenReturn(String.valueOf(RETRY_AFTER_MAXIMUM_WAIT.plusSeconds(1).toSeconds()));
    AtomicInteger attempts = givenGetFailingWith(response, Integer.MAX_VALUE, "unreachable");

    assertThatThrownBy(() -> interceptor().intercept(context))
        .isInstanceOf(RetryAfterExceedsMaximumWaitException.class)
        .hasMessageContaining("exceeds the configured maximum wait");

    assertThat(attempts).hasValue(1);
  }

  @Test
  void refuses_to_police_reactive_return_types() {
    when(context.getReturnType()).thenReturn(returnType);
    when(returnType.isAsyncOrReactive()).thenReturn(true);
    lenient().when(context.getDeclaringType()).thenReturn((Class) Object.class);
    lenient().when(context.getMethodName()).thenReturn("fetch");

    assertThatThrownBy(() -> interceptor().intercept(context))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("reactive or asynchronous");
  }

  private AtomicInteger givenGetFailingWith(
      HttpResponse<?> response, int failuresBeforeSuccess, String eventualResult) {
    givenSynchronousMethod();
    when(context.hasAnnotation(Get.class)).thenReturn(true);
    return givenProceedFailingWith(response, failuresBeforeSuccess, eventualResult);
  }

  private AtomicInteger givenProceedFailingWith(
      HttpResponse<?> response, int failuresBeforeSuccess, String eventualResult) {
    AtomicInteger attempts = new AtomicInteger();
    when(context.proceed(any(Interceptor.class)))
        .thenAnswer(
            invocation -> {
              if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new HttpClientResponseException("failing", response);
              }
              return eventualResult;
            });
    return attempts;
  }

  private void givenSynchronousMethod() {
    when(context.getReturnType()).thenReturn(returnType);
    lenient().when(context.getDeclaringType()).thenReturn((Class) Object.class);
    lenient().when(context.getMethodName()).thenReturn("fetch");
  }

  private static HttpResponse<?> status(int code) {
    HttpResponse<?> response = org.mockito.Mockito.mock(HttpResponse.class);
    lenient().when(response.code()).thenReturn(code);
    lenient().when(response.getStatus()).thenReturn(HttpStatus.valueOf(code));
    return response;
  }
}
