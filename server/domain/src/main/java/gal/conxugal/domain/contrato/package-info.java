/**
 * Contratos menores: the {@link gal.conxugal.domain.contrato.ContratoMenor} aggregate a stored
 * contract is, its {@link gal.conxugal.domain.contrato.ContratoMenorId} identity, the
 * {@link gal.conxugal.domain.contrato.ContratoMenorRepository} port that stores them, and the
 * {@link gal.conxugal.domain.contrato.ContratoMenorSource} port that retrieves one published
 * {@link gal.conxugal.domain.contrato.ContratoMenorSourcePage} at a time — the only shape the
 * source offers.
 *
 * <p>It also holds the vocabulary a browse read is asked in that is this family's own — a
 * {@link gal.conxugal.domain.contrato.YearSelection} and a
 * {@link gal.conxugal.domain.contrato.SortKey}, the two whose values name contratos menores rather
 * than pagination; the direction such a read runs in is
 * {@link gal.conxugal.commons.pagination.SortDirection}, shared with every other paginated list.
 * Beside them sit the {@link gal.conxugal.domain.contrato.VisibleContratoMenor} row a browse read
 * answers with and the {@link gal.conxugal.domain.contrato.VisibleContratoMenorRepository} port
 * that serves it.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.contrato;

import org.jspecify.annotations.NullMarked;
