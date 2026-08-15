/**
 * Which of the dropdown's three bodies the filter is asking for.
 *
 * Named once and shared because two components read it separately — the search
 * box to say what it narrows, the list to decide what to draw — and a
 * three-way condition written out on both sides is one that can disagree with
 * itself. It already did: the box named a list the other half had hidden.
 */
export type FilterAnswer = 'tree' | 'matches' | 'none';

/** `query` is expected trimmed: a blank filter has asked nothing. */
export function filterAnswer(query: string, matchCount: number): FilterAnswer {
  if (query === '') {
    return 'tree';
  }
  return matchCount === 0 ? 'none' : 'matches';
}
