/**
 * Driven-side adapters of the conxugal server.
 *
 * <p>Implements the domain ports against external systems — PostgreSQL
 * persistence, external scrapers/ingestors for contratosdegalicia.gal, and
 * exporters — together with the Micronaut wiring for them. Depends on
 * {@code domain} only; it must not depend on {@code application}.
 */
package gal.conxugal.infrastructure;
