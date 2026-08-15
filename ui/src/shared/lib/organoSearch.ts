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
 * A blank query matches everything, as `includes('')` does. A surface that
 * offers nothing until something is typed trims and checks that itself.
 */
export function matches(name: string, query: string): boolean {
  return foldForSearch(name).includes(foldForSearch(query));
}
