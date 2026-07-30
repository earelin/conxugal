package gal.conxugal.domain.organo;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when a term would end up sharing a name, case-insensitively, with one of its
 * siblings — on a create, a rename, or a move that lands it beside a new set of siblings.
 * Roots count as siblings of each other.
 */
public class DuplicateSiblingNameException extends RuntimeException {

  private final @Nullable String name;

  public DuplicateSiblingNameException(String name) {
    super(messageFor(name));
    this.name = name;
  }

  /**
   * The refusal as the {@code infrastructure} adapter raises it, translating the unique index
   * that enforces the rule so that two writes racing are refused the same way a checked one
   * is. A re-parent is addressed by ids alone, so that one arrives with a null {@code name}.
   */
  public DuplicateSiblingNameException(@Nullable String name, Throwable cause) {
    super(messageFor(name), cause);
    this.name = name;
  }

  /** The colliding name, or null when the refusal came from a re-parent (see above). */
  public @Nullable String getName() {
    return name;
  }

  private static String messageFor(@Nullable String name) {
    return name == null
        ? "A sibling term already uses this name"
        : "A sibling term is already named: %s".formatted(name);
  }
}
