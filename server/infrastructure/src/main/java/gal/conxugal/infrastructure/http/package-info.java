/**
 * The shared resilient outbound HTTP package: a retry, rate-limiter and circuit-breaker decorator
 * over Micronaut's {@link io.micronaut.http.client.BlockingHttpClient}, used by every driven-side
 * adapter that calls an external source. Depends on {@code domain} only, same as the rest of
 * {@code infrastructure}; carries no source-specific knowledge — each source declares its own
 * {@code @ConfigurationProperties} record implementing {@link
 * gal.conxugal.infrastructure.http.ResilientHttpClientSettings} and builds both clients from it,
 * the raw one through {@link gal.conxugal.infrastructure.http.RawHttpClients} and the decorated
 * one through {@link gal.conxugal.infrastructure.http.ResilientHttpClient}.
 */
package gal.conxugal.infrastructure.http;
