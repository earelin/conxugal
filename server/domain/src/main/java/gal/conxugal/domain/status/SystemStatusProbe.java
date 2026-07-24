package gal.conxugal.domain.status;

/**
 * Port for reading the instance's current status. Implemented by an adapter in the
 * {@code infrastructure} module.
 *
 * <p>There is deliberately no way to ask for a past snapshot: each call assembles the status as
 * it is now, and nothing is cached behind the port.
 */
public interface SystemStatusProbe {

  SystemStatus currentStatus();
}
