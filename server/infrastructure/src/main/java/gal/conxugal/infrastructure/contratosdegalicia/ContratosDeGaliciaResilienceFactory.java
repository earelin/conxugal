package gal.conxugal.infrastructure.contratosdegalicia;

import gal.conxugal.infrastructure.http.ResilientClientPolicies;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import java.time.Clock;

/**
 * Builds the three policy beans the resilience advice runs contratosdegalicia calls under.
 *
 * <p>One instance of each, shared by every client interface pointed at the source: two rate
 * limiters would each hold a full budget and pace independently, which silently doubles the request
 * rate the source sees.
 */
@Factory
class ContratosDeGaliciaResilienceFactory {

  static final String NAME = "contratosdegalicia";

  @Singleton
  RateLimiter contratosDeGaliciaRateLimiter(ContratosDeGaliciaResilienceConfiguration settings) {
    return RateLimiter.of(
        NAME,
        ResilientClientPolicies.rateLimiter(
            settings.rateLimitRefreshPeriod(), settings.maximumPermitWait()));
  }

  @Singleton
  CircuitBreaker contratosDeGaliciaCircuitBreaker(
      ContratosDeGaliciaResilienceConfiguration settings) {
    return CircuitBreaker.of(
        NAME,
        ResilientClientPolicies.circuitBreaker(
            settings.breakerFailureRateThreshold(), settings.breakerOpenStateDuration()));
  }

  @Singleton
  Retry contratosDeGaliciaRetry(ContratosDeGaliciaResilienceConfiguration settings) {
    return Retry.of(
        NAME,
        ResilientClientPolicies.retry(
            settings.retryMaxAttempts(),
            settings.retryBaseDelay(),
            settings.retryAfterMaximumWait(),
            Clock.systemUTC()));
  }
}
