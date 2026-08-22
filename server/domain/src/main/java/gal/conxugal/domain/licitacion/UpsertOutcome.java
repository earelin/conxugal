package gal.conxugal.domain.licitacion;

import java.util.Objects;

/**
 * How one procedure landed: the identity it is stored under, and which branch the write took.
 * Reported by the write itself because only the write can tell the branches apart — once the row
 * is stored, nothing afterwards can say whether it was new.
 *
 * <p>Both halves are needed and neither is recoverable later. The identity is what a procedure's
 * children are attached to, inside the same transaction that wrote it; the operation is what the
 * run's outcome counts, and a caller that read first to work it out would be racing whoever wrote
 * next.
 */
public record UpsertOutcome(LicitacionId id, UpsertOperation operation) {

  public UpsertOutcome {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }
}
