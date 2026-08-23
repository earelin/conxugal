/**
 * Operadores económicos: the {@link gal.conxugal.domain.operador.OperadorEconomico} aggregate
 * the awardee of every contract resolves to, the
 * {@link gal.conxugal.domain.operador.NomeAlternativo} names it has also been published under,
 * and the {@link gal.conxugal.domain.operador.OperadorRepository} port that stores them.
 *
 * <p>A <em>unión temporal de empresas</em> is one of these too, marked as such and — where the
 * source declines to identify it — holding no fiscal identifier at all.
 * {@link gal.conxugal.domain.operador.UteMembership} therefore relates one entry to another rather
 * than hanging a list off a contract, which is what lets it be read from either end, and
 * {@link gal.conxugal.domain.operador.UteMembershipRepository} is its port.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.operador;

import org.jspecify.annotations.NullMarked;
