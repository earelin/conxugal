package gal.conxugal.domain.organo.taxonomia;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The sibling-name rule shared by {@link CreateTermo}, {@link RenameTermo} and {@link MoveTermo}.
 * The comparison is case-insensitive, matching the {@code (parent_id, lower(name))} unique index
 * that backs it in the schema; that index remains an integrity backstop, but this check is what
 * produces the refusal a caller sees.
 */
final class SiblingNames {

  private SiblingNames() {
  }

  /**
   * Refuses {@code name} if any of {@code siblings} already carries it. A rename or a move
   * passes the term's own id as {@code excludedTermoId} so it does not collide with itself —
   * renaming a term to the name it already has, or moving it under the parent it already sits
   * in, leaves the taxonomy unambiguous and is not a duplicate.
   */
  static void requireAvailable(
      List<Termo> siblings, String name, @Nullable UUID excludedTermoId,
      @Nullable UUID parentId) {
    boolean taken =
        siblings.stream()
            .filter(sibling -> !Objects.equals(sibling.id(), excludedTermoId))
            .anyMatch(sibling -> sibling.name().equalsIgnoreCase(name));
    if (taken) {
      throw new DuplicateSiblingNameException(name, parentId);
    }
  }
}
