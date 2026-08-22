package gal.conxugal.domain.licitacion;

import java.util.Objects;

/**
 * How one procedure landed: the identity it is stored under, and whether the store held it
 * before. Reported by the write itself because only the write can tell the two apart — once the
 * row is stored, nothing afterwards can say whether it was new.
 *
 * <p>Both halves are needed and neither is recoverable later. The identity is what a procedure's
 * children are attached to, inside the same transaction that wrote it; {@code added} is what the
 * run's outcome counts, and a caller that read first to work it out would be racing whoever wrote
 * next.
 */
public record UpsertOutcome(LicitacionId id, boolean added) {

  public UpsertOutcome {
    Objects.requireNonNull(id, "id must not be null");
  }
}
