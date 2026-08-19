import type { OrganoFamily } from '../../shared/entities/organo';

/**
 * The years an Órgano has visible contratos menores in, newest first.
 *
 * Spelled as a non-empty list because that is what the wire promises and what
 * every reader of it relies on: the first entry is the year the section opens
 * on, and a section with no year is no section at all — it is never mounted,
 * because the summary that would mount it is absent.
 */
export type PublicationYears = [number, ...number[]];

/**
 * What this section says about itself, answered before a single contract is
 * read. It arrives inside the Órgano page's one request and is the only shape
 * of a family summary anything narrows — the shared core holds the envelope and
 * leaves the interior `unknown`, so this module is where the family's own
 * schema begins.
 */
export interface ContratosMenoresSummary {
  years: PublicationYears;
  partial: boolean;
  updating: boolean;
}

/**
 * Takes the summary at its published word, like every other read in this module
 * — nothing here validates a response. What the type adds over the `unknown` it
 * narrows is the non-empty year list, so the code downstream can be read against
 * a stated invariant rather than an assumed one.
 */
export function sectionSummary(family: OrganoFamily): ContratosMenoresSummary {
  return family.summary as ContratosMenoresSummary;
}
