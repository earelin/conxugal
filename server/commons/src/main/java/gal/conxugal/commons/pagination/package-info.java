/**
 * Vocabulary every paginated list shares, held once so that no two of them can spell the same
 * thing differently.
 *
 * <p>It carries the spellings the API publishes, which is the one place this package steps outside
 * the rule that shared utility code holds no transport content. That is deliberate: the alternative
 * is the same two words parsed in as many places as there are lists, which is exactly the
 * divergence holding them here prevents.
 *
 * <p>{@code @NullMarked}: every type, field, parameter and return value in this package is
 * non-null unless explicitly annotated {@link org.jspecify.annotations.Nullable}.
 */
@NullMarked
package gal.conxugal.commons.pagination;

import org.jspecify.annotations.NullMarked;
