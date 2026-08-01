/**
 * The Órganos taxonomy: the {@link gal.conxugal.domain.organo.taxonomia.Termo} aggregate, its
 * {@link gal.conxugal.domain.organo.taxonomia.TermoRepository} port, the use cases that build
 * and reshape the tree, and the rejections they raise. Terms exist to classify the Órganos of
 * the enclosing package, so they sit beneath it rather than beside it.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.domain.organo.taxonomia;

import org.jspecify.annotations.NullMarked;
