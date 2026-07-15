package gal.conxugal.domain.time;

import java.time.Instant;

/**
 * Port for reading the current instant. Implemented by a {@code java.time.Clock}-backed
 * adapter in the {@code infrastructure} module; tests supply a fixed instant.
 */
public interface Clock {

  Instant instant();
}
