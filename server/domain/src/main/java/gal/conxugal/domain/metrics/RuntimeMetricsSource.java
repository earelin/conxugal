package gal.conxugal.domain.metrics;

/**
 * Port for reading the instance's current runtime state. Implemented by an adapter in the
 * {@code infrastructure} module.
 *
 * <p>There is deliberately no way to ask for a past sample: each call assembles the figures as
 * they are now, and nothing accumulates behind the port.
 */
public interface RuntimeMetricsSource {

  RuntimeMetrics currentSample();
}
