package gal.conxugal.infrastructure.time;

import gal.conxugal.domain.time.Clock;
import jakarta.inject.Singleton;
import java.time.Instant;

/** {@link Clock} adapter backed by {@link java.time.Clock#systemUTC()}. */
@Singleton
public class SystemClock implements Clock {

  private final java.time.Clock clock = java.time.Clock.systemUTC();

  @Override
  public Instant instant() {
    return clock.instant();
  }
}
