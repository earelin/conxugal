// The backtracking sonarjs warns about is bounded here: this only ever matches
// against a rounded Number's own decimal form, never longer than ~21 characters.
// eslint-disable-next-line sonarjs/super-linear-regex
const thousandsBoundary = /\B(?=(\d{3})+(?!\d))/g;

export function formatCount(n: number): string {
  return Math.round(n).toString().replace(thousandsBoundary, ' ');
}

/**
 * An amount of euros, written `12.480,00 €`.
 *
 * The grouping mark is a dot rather than the space `formatCount` uses, which is
 * deliberate rather than an inconsistency: a count is read as a quantity, while
 * a sum of money is read against other sums of money, where the dot is what
 * Galician writes.
 *
 * Both marks are taken from the formatter's own parts by role and replaced,
 * rather than patched into its output — which character plays which role depends
 * on the locale data the runtime actually resolved, and a build falling back to
 * '.' as the decimal mark would otherwise have that mark rewritten as a grouping
 * one.
 */
export function formatEuros(amount: number): string {
  const figure = new Intl.NumberFormat('gl-ES', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
    .formatToParts(amount)
    .map((part) => {
      if (part.type === 'group') {
        return '.';
      }
      return part.type === 'decimal' ? ',' : part.value;
    })
    .join('');
  return `${figure} €`;
}
