/**
 * Domain core of the conxugal server.
 *
 * <p>Holds the contract/tender model and business rules — entities, value objects
 * and domain services — plus the ports (interfaces) that the {@code application}
 * and {@code infrastructure} modules drive and implement respectively. This module
 * depends on nothing outside itself; it is free of transport and persistence
 * concerns, though its classes may carry Micronaut dependency-injection
 * annotations.
 */
package gal.conxugal.domain;
