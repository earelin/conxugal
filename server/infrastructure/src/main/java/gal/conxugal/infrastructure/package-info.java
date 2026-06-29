/**
 * Driven-side adapters of the conxugal server (ADR-0002).
 *
 * <p>Implements the domain ports against external systems — PostgreSQL
 * persistence (ADR-0001), external scrapers/ingestors for contratosdegalicia.gal,
 * and exporters — together with the Micronaut wiring for them. Depends on
 * {@code domain} only; it must not depend on {@code application}.
 */
package gal.conxugal.infrastructure;
