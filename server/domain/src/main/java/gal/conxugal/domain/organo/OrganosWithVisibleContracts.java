package gal.conxugal.domain.organo;

import java.util.Collection;
import java.util.Set;

/**
 * Port answering which of a set of Órganos hold at least one visible contract. Implemented once
 * per contract family in the {@code infrastructure} module, each defining <em>visible</em> for its
 * own family; the catalogue read composes the answers and reaches into no family's tables. A new
 * family joins the visible set by adding an implementation, and nothing that reads the catalogue
 * changes.
 *
 * <p>It answers <strong>a set for a set</strong> rather than a boolean per Órgano: the caller
 * already holds the whole catalogue, so one round trip per family is the whole cost of the
 * question.
 */
public interface OrganosWithVisibleContracts {

  /**
   * The subset of {@code candidates} this family holds a visible contract for. Answers an empty
   * set for an empty one, and never returns an Órgano that was not asked about.
   */
  Set<OrganoId> among(Collection<OrganoId> candidates);
}
