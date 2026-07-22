/**
 * Runtime metrics: the {@link gal.conxugal.domain.metrics.RuntimeMetrics} sample and the
 * {@link gal.conxugal.domain.metrics.RuntimeMetricsSource} port that assembles the current one.
 *
 * <p>Nothing here is stored: a sample is a value assembled on demand and handed to whoever asked
 * for it.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.metrics;

import org.jspecify.annotations.NullMarked;
