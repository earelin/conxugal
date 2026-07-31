package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Port for reading and reshaping the Órganos taxonomy. Implemented by the {@code
 * infrastructure} module.
 */
public interface TermoRepository {

  /**
   * Every term, ordered by name. The order is part of this port's own contract, not the
   * adapter's private choice: the taxonomy read endpoint serves this list verbatim.
   */
  List<Termo> findAllOrderByName();

  Optional<Termo> findById(UUID id);

  @Insert
  Termo insert(Termo termo);

  /** Renames an existing term in place. */
  void updateName(@Id UUID id, String name);

  /**
   * Re-parents an existing term; a null {@code parentId} moves it to the root. Pointing a
   * term at itself is refused by the schema, leaving only multi-term cycles for a caller's
   * own acyclic check.
   */
  void updateParentId(@Id UUID id, @Nullable UUID parentId);

  void deleteById(UUID id);

  /** True if any term names the given id as its parent — the delete rule's child check. */
  boolean existsByParentId(UUID parentId);

  /**
   * Every term whose parent is the given id. A null {@code parentId} is a legal argument
   * and returns the roots — what the sibling-name rule needs to check roots against each
   * other too. A bare derived query binds {@code parent_id = :parentId}, which never
   * matches when the argument is null; the {@code infrastructure} implementation must use
   * an explicit {@code parent_id IS NOT DISTINCT FROM :parentId} query instead.
   */
  List<Termo> findByParentId(@Nullable UUID parentId);
}
