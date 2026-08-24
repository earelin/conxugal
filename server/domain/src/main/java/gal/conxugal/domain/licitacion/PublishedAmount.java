package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.money.Money;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An amount together with the value-added-tax basis the source published it on. Two values because
 * the source states two: the figure, and — for the base budget and the estimated value — whether it
 * is quoted with tax or without.
 *
 * <p>The basis is null where the source marked none. That is not a gap to be filled by inference:
 * an unmarked figure is one whose basis nobody published, and recording an inference as though it
 * were published is the defect this pairing exists to prevent.
 *
 * @param amount the figure exactly as published, at the scale it was published at
 * @param vatBasis the basis the source labelled the figure with, or null where it labelled none
 */
public record PublishedAmount(Money amount, @Nullable VatBasis vatBasis) {

  public PublishedAmount {
    Objects.requireNonNull(amount, "amount must not be null");
  }
}
