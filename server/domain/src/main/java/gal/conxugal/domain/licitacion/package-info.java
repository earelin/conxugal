/**
 * Licitacións: the {@link gal.conxugal.domain.licitacion.Licitacion} aggregate a stored tender
 * procedure is, its {@link gal.conxugal.domain.licitacion.LicitacionId} identity, and the
 * {@link gal.conxugal.domain.licitacion.LicitacionRepository} port that stores them — matching on
 * the source's publication identifier and answering an
 * {@link gal.conxugal.domain.licitacion.UpsertOutcome} that says which branch the write took.
 *
 * <p>Beside the aggregate sit the four vocabularies it refers to, each a row of its own so a value
 * the source publishes on a thousand procedures is held once: the
 * {@link gal.conxugal.domain.licitacion.LicitacionState}, keyed on the {@code estado} code the
 * source publishes and carrying a label two codes are allowed to share, and the three types a
 * record publishes as a bare name — {@link gal.conxugal.domain.licitacion.LicitacionContractType},
 * {@link gal.conxugal.domain.licitacion.LicitacionProcedureType} and
 * {@link gal.conxugal.domain.licitacion.LicitacionTramitacionType}. Each has an identifier type
 * and a port of its own, so the compiler refuses a procedure type where a contract type belongs,
 * and none of them is seeded or validated against: a value the source has not published before
 * simply creates its row.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.licitacion;

import org.jspecify.annotations.NullMarked;
