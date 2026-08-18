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

/**
 * Decimal digits only, for the same reason `askedPage` insists on them: `Number`
 * would otherwise read `0x7e8` as 2024 and `2.024e3` as 2024 too, so a URL could
 * name a year in a spelling the API never takes. Deliberately not trimmed, which
 * is where it parts company with the typed page box — whitespace in a query
 * string was put there by something, not typed by somebody.
 */
const DIGITS = /^\d+$/;

/**
 * The year the section opens on: the one the URL asks for where the Órgano has
 * contracts in it, and the most recent otherwise.
 *
 * An unknown year is not an error and not an empty list — it is a URL that has
 * outlived the selection it named, which happens as soon as an import moves on.
 * Landing on the default is the answer; `years` is newest first, so its first
 * entry is that default.
 */
export function chosenYear(asked: string | null, years: PublicationYears): number {
  if (!DIGITS.test(asked ?? '')) {
    return years[0];
  }
  const year = Number(asked);
  return years.includes(year) ? year : years[0];
}
