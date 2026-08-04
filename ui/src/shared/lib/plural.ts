/** A counted noun carrying both of its forms, so neither can be used without the other. */
export interface Word {
  readonly singular: string;
  readonly plural: string;
}

/**
 * Picks the form a count calls for.
 *
 * The rule lives here rather than at each call site so that a language whose
 * plural is not simply "one versus the rest" — or a copy change that adds a
 * zero form — is one function to revisit instead of every place a count is
 * rendered.
 */
export function singularOrPlural(count: number, word: Word): string {
  return count === 1 ? word.singular : word.plural;
}
