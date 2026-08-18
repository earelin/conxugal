/**
 * The abbreviated Galician months, pinned rather than read off the runtime.
 *
 * Two reasons, and either alone would be enough. `Intl` resolves `gl-ES` to
 * `12 de mar. de 2025`, which is not the spelling this list is drawn in; and the
 * data behind it differs between browser builds, so a suite asserting on a
 * formatted date would pass on one engine and fail on another. The set is the
 * one FEAT-0007's screens established.
 */
const MONTHS = ['xan', 'feb', 'mar', 'abr', 'mai', 'xuñ', 'xul', 'ago', 'set', 'out', 'nov', 'dec'];

/**
 * A publication date as `12 mar 2025`.
 *
 * Split rather than parsed through `Date`: `new Date('2025-03-12')` is midnight
 * UTC, so anywhere west of Greenwich the calendar date a reader is shown would
 * be the day before the one the source published.
 */
export function formatPublicationDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${Number(day)} ${MONTHS[Number(month) - 1]} ${year}`;
}

/**
 * An awarded amount as `12.480,00 €`.
 *
 * The separators are taken from the formatter's own parts by role and replaced
 * with the two this list writes, rather than patched into its output — which
 * character plays which role depends on the locale data the runtime actually
 * resolved, and a build falling back to '.' as the decimal mark would otherwise
 * have that mark rewritten as a grouping one. The same reasoning as
 * `metricsFormat.ts`, and the same reason the marks are stated here at all.
 */
export function formatAmount(amount: number): string {
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
