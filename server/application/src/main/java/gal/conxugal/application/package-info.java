/**
 * Driving-side entry points of the conxugal server.
 *
 * <p>Hosts the REST API, schedulers and other triggers, plus the use-case
 * orchestration that coordinates the {@code domain}. This module is the single
 * runnable artifact and the single origin that also serves the built UI assets.
 * Depends on {@code domain} only; infrastructure adapters are supplied at runtime
 * through Micronaut's DI container.
 */
package gal.conxugal.application;
