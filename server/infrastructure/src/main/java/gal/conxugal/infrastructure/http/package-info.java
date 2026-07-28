/**
 * The shared resilient outbound HTTP package: a retry, rate-limiter and circuit-breaker decorator
 * over Micronaut's {@link io.micronaut.http.client.BlockingHttpClient}, used by every driven-side
 * adapter that calls an external source. Depends on {@code domain} only, same as the rest of
 * {@code infrastructure}; carries no source-specific knowledge — each source still declares its
 * own raw-client factory and configuration record and hands both to {@link
 * gal.conxugal.infrastructure.http.ResilientHttpClient}.
 */
package gal.conxugal.infrastructure.http;
