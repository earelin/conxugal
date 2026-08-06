/**
 * Contratos menores: the {@link gal.conxugal.domain.contrato.ContratoMenor} aggregate a stored
 * contract is, its {@link gal.conxugal.domain.contrato.ContratoMenorId} identity, the
 * {@link gal.conxugal.domain.contrato.ContratoMenorRepository} port that stores them, and the
 * {@link gal.conxugal.domain.contrato.ContratoMenorSource} port that retrieves one published
 * {@link gal.conxugal.domain.contrato.ContratoMenorSourcePage} at a time — the only shape the
 * source offers.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.contrato;

import org.jspecify.annotations.NullMarked;
