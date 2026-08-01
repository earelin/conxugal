package gal.conxugal.infrastructure.http;

import io.micronaut.aop.Around;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.annotation.Header;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies the outbound resilience policy — rate limiting, circuit breaking and retry — to a
 * declarative {@code @Client} interface or one of its methods.
 *
 * <p>Micronaut implements a {@code @Client} interface with introduction advice; around advice on
 * the same element runs outside that implementation, so this annotation wraps the whole exchange,
 * response binding included.
 *
 * <p>Only synchronous methods are supported: a reactive or asynchronous return type would hand the
 * interceptor a publisher rather than an outcome, and every policy would judge the call on how fast
 * that publisher was constructed. {@link ResilientClientInterceptor} rejects such a method rather
 * than letting it pass unpoliced.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@Header(name = HttpHeaders.USER_AGENT, value = "${conxugal.user-agent}")
public @interface ResilientClient {

  /**
   * Whether a request this annotation covers may be sent more than once. {@code GET} and {@code
   * HEAD} are retryable without it; any other method is retried only where this says so, so the
   * policy can never silently double-submit a state-changing call.
   *
   * <p>Set it on a {@code @Post} that is pure retrieval — contratosdegalicia exposes searches that
   * way, with the query in the body.
   */
  boolean idempotent() default false;
}
