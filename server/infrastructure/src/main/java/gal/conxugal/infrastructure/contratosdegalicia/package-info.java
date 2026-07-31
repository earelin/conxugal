/**
 * Wiring shared by every client that talks to contratosdegalicia.gal: the resilience configuration,
 * the policy beans built from it, and the filter that identifies us. Source-scoped rather than
 * adapter-scoped — the site is one counterparty however many things conxugal asks it for, and the
 * rate budget is shared across all of them.
 */
package gal.conxugal.infrastructure.contratosdegalicia;
