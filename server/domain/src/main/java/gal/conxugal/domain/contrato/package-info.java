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
 * than pagination. The direction such a read runs in is the pagination library's own
 * {@link io.micronaut.data.model.Sort.Order.Direction}, since the ordering reaches the store as a
 * {@link io.micronaut.data.model.Sort} and a second enum would only be mapped onto that one.
 * Beside them sit the {@link gal.conxugal.domain.contrato.VisibleContratoMenor} row a browse read
 * answers with and the {@link gal.conxugal.domain.contrato.VisibleContratoMenorRepository} port
 * that serves it.
 *
 * <p>What a reader meets before any of that is the
 * {@link gal.conxugal.domain.contrato.ContratosMenoresSection} an Órgano presents — the years it
 * offers and the two statements it makes about the data behind them — which
 * {@link gal.conxugal.domain.contrato.DescribeContratosMenoresSection} derives, and answers with
 * nothing at all for an Órgano that presents no section.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.contrato;

import org.jspecify.annotations.NullMarked;
