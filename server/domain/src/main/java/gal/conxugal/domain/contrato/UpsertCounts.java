package gal.conxugal.domain.contrato;

/**
 * How a batch of contracts landed: how many rows the store did not hold before, and how many it
 * already held and refreshed in place. Reported by the write itself because only the write can
 * tell the two apart — once a batch is stored, nothing afterwards can say which of its rows were
 * new.
 */
public record UpsertCounts(int added, int refreshed) {
}
