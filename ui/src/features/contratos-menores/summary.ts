import type { OrganoFamily } from '../../shared/entities/organo';

/**
 * What this section says about itself, answered before a single contract is
 * read. It arrives inside the Órgano page's one request and is the only shape
 * of a family summary anything narrows — `shared/` holds the envelope and
 * leaves the interior `unknown`, so this module is where the family's own
 * schema begins.
 */
export interface ContratosMenoresSummary {
  /**
   * The publication years the Órgano has visible contratos menores in, newest
   * first. Never empty: a section with no year is no section at all, so it is
   * never mounted.
   */
  years: number[];
  partial: boolean;
  updating: boolean;
}

export function sectionSummary(family: OrganoFamily): ContratosMenoresSummary {
  return family.summary as ContratosMenoresSummary;
}

/**
 * Decimal digits only, for the same reason `askedPage` insists on them: `Number`
 * would otherwise read `0x7e9` as 2025 and `2.025e3` as 2025 too, so a URL could
 * name a year in a spelling the API never takes.
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
export function chosenYear(asked: string | null, years: number[]): number {
  if (!DIGITS.test(asked ?? '')) {
    return years[0];
  }
  const year = Number(asked);
  return years.includes(year) ? year : years[0];
}
