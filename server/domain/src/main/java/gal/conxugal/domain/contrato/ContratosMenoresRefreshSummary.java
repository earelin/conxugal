package gal.conxugal.domain.contrato;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What one Órgano's refresh stored, and what cut it off if anything did.
 *
 * <p><strong>It carries no {@link
 * gal.conxugal.domain.organo.ContratosMenoresImportStatus}</strong>, and the absence is the whole
 * reason this type exists rather than {@link ContratosMenoresImportSummary}. Every value of that
 * enum is a statement about an <em>initial</em> import: {@code COMPLETE} means the stored count
 * reached the source's own, and {@code INCOMPLETE} means resume this later. A refresh can say
 * neither — it leaves the Órgano exactly as {@code COMPLETE} as it found it, and it never converges
 * a count because it never reads a whole history.
 *
 * <p>Forcing it into that type has two shapes and both are wrong. Answering {@code complete(...)}
 * works by accident and asserts something false; answering anything else is mapped by the
 * orchestrator to the history floor's ending, so every successful nightly refresh would report
 * having read every window down to 2018 without its count matching.
 *
 * <p>{@code stoppedBy} is present when the refresh was cut off part-way, and it is the fact that
 * decides whether the refresh mark is written: only a walk nothing interrupted may move it.
 */
public record ContratosMenoresRefreshSummary(
    int added, int refreshed, @Nullable StopReason stoppedBy) {

  /**
   * A refresh that read every window down to its floor. Named for the ending rather than for a
   * status, since {@code COMPLETE} is the word this type exists to avoid.
   */
  public static ContratosMenoresRefreshSummary clean(int added, int refreshed) {
    return new ContratosMenoresRefreshSummary(added, refreshed, null);
  }

  /**
   * A refresh cut off at a batch boundary. What it stored still stands, and the refresh mark is
   * left where it was, so the next run reads the same period from the same floor.
   */
  public static ContratosMenoresRefreshSummary stopped(
      int added, int refreshed, StopReason stoppedBy) {
    return new ContratosMenoresRefreshSummary(
        added, refreshed, Objects.requireNonNull(stoppedBy, "stoppedBy must not be null"));
  }
}
