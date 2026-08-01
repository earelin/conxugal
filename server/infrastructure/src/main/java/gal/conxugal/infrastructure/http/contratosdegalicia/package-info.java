/**
 * Wiring shared by every client that talks to contratosdegalicia.gal: the resilience configuration
 * and the policy beans built from it. Source-scoped rather than adapter-scoped — the site is one
 * counterparty however many things conxugal asks it for, and the rate budget is shared across all
 * of them.
 *
 * <p>Nested under the advice it configures rather than sitting beside the adapters, so one place
 * holds the whole outbound story: what the policy means in the enclosing package, one source's
 * numbers here.
 */
package gal.conxugal.infrastructure.http.contratosdegalicia;
