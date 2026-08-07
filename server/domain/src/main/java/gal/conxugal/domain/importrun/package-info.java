/**
 * Import runs: the {@link gal.conxugal.domain.importrun.ImportRun} record one triggered import
 * writes, its {@link gal.conxugal.domain.importrun.ImportRunId} identity, the
 * {@link gal.conxugal.domain.importrun.ImportRunRepository} port whose claim admits one live run
 * across the whole system, and the {@link gal.conxugal.domain.importrun.ImportRunReport} a reader
 * gets back.
 *
 * <p>It sits beside the aggregates it covers rather than inside one of them because a run belongs
 * to no single importer: the same record, and the same guard, serve the catalogue import and the
 * contratos menores import alike.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.importrun;

import org.jspecify.annotations.NullMarked;
