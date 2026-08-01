package gal.conxugal.domain.organo.taxonomia;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when a create, rename or move would leave two terms sharing a name under one parent,
 * where the tree is navigated by name and the two would be indistinguishable. A null
 * {@code parentId} means the collision is among the roots, which are siblings of each other.
 */
public class DuplicateSiblingNameException extends RuntimeException {

  private final String name;
  private final @Nullable UUID parentId;

  public DuplicateSiblingNameException(String name, @Nullable UUID parentId) {
    super(parentId == null
        ? "A root term named %s already exists".formatted(name)
        : "A term named %s already exists under parent %s".formatted(name, parentId));
    this.name = name;
    this.parentId = parentId;
  }

  public String getName() {
    return name;
  }

  public @Nullable UUID getParentId() {
    return parentId;
  }
}
