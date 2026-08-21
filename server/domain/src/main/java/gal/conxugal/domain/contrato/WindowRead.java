package gal.conxugal.domain.contrato;

import org.jspecify.annotations.Nullable;

/**
 * How one window ended: what it stored, and the count the source reported as it was read.
 *
 * <p>{@code stoppedBy} is present when the window was cut off part-way. The pages already stored
 * stand and are counted here, but nothing beyond that window is the walk's to read — and {@code
 * recordsTotal} then says nothing, because the window it would have been judged against was never
 * read out.
 */
public record WindowRead(
    int added, int refreshed, long recordsTotal, @Nullable StopReason stoppedBy) {}
