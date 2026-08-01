/**
 * The shared outbound resilience advice: {@link gal.conxugal.infrastructure.http.ResilientClient}
 * marks a declarative {@code @Client} interface, and {@code ResilientClientInterceptor} runs its
 * methods under a rate limiter, a circuit breaker and a retry. Carries no source-specific
 * knowledge — {@link gal.conxugal.infrastructure.http.ResilientClientPolicies} decides what the
 * numbers mean, and each source supplies its own from a subpackage of this one.
 */
package gal.conxugal.infrastructure.http;
