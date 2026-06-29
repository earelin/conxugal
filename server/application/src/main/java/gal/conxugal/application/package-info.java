/**
 * Driving-side entry points of the conxugal server (ADR-0002).
 *
 * <p>Hosts the REST API (ADR-0001), schedulers and other triggers, plus the
 * use-case orchestration that coordinates the {@code domain}. This module is the
 * single runnable artifact and the single origin that also serves the built UI
 * assets (ADR-0003). Depends on {@code domain} only; infrastructure adapters are
 * supplied at runtime through Micronaut's DI container.
 */
package gal.conxugal.application;
