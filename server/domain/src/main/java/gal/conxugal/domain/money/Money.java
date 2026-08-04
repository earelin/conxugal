package gal.conxugal.domain.money;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount of money. Its own type so that a figure cannot be added to a count, a page number or
 * a year by accident, and so the one place amounts are summed sums something that knows it is
 * money.
 *
 * <p>No currency is carried: every figure this system stores comes from a source that publishes
 * in euros and states no currency, so recording one per amount would be storing a fact nobody
 * published. If a second currency ever arrives it becomes a field, and that is a change to make
 * then.
 *
 * <p>The figure is held exactly as it was published — nothing here rounds or rescales it — so
 * equality is scale-sensitive: {@code 1.0} and {@code 1.00} are two different published
 * spellings and two different values. Use {@link #plus} for arithmetic, which is exact decimal
 * addition and never binary floating point.
 */
@TypeDef(type = DataType.BIGDECIMAL, converter = MoneyConverter.class)
public record Money(BigDecimal value) {

  /**
   * Nothing, as an amount — the identity a sum starts from, so that summing no amounts at all
   * answers with money rather than with a null or a bare zero. It carries no scale of its own, so
   * adding it to a published figure leaves that figure's scale exactly as published.
   */
  public static final Money ZERO = new Money(BigDecimal.ZERO);

  public Money {
    Objects.requireNonNull(value, "value must not be null");
  }

  /** The two amounts added exactly, at whatever scale exact addition needs. */
  public Money plus(Money other) {
    Objects.requireNonNull(other, "other must not be null");
    return new Money(value.add(other.value));
  }
}
