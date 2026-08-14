import { deburr } from 'es-toolkit';

/**
 * The form names are compared in. Galician names are full of accents and nobody
 * types them into a search box, so «educacion» has to find «Educación».
 */
export function foldForSearch(text: string): string {
  return deburr(text).toLowerCase();
}

/**
 * Whether `query` appears anywhere in `name`, ignoring case and accents. Both
 * sides are folded, so the match holds whichever of the two carries the accent.
 *
 * It is `includes`-style, so an interior fragment matches and a blank query
 * matches everything. Offering nothing until something is typed is a rule about
 * what a surface shows, not about what matches, so the caller trims first.
 */
export function matches(name: string, query: string): boolean {
  return foldForSearch(name).includes(foldForSearch(query));
}
