/**
 * Decimal digits only. `Number` would otherwise read a page number in another
 * base or in exponent form — `0x10` as 16, `1e1` as 10 — and page somewhere the
 * reader did not ask for, which is the one thing the jump must not do.
 */
const DIGITS = /^\d+$/;

/**
 * The page a typed jump asks for, or `null` where it asks for none.
 *
 * A blank box, anything that is not a run of digits, and a page outside the
 * selection are all refused rather than corrected: silently paging somewhere
 * adjacent to what was asked for is worse than not moving at all.
 */
export function askedPage(typed: string | null, totalPages: number): number | null {
  if (!DIGITS.test(typed?.trim() ?? '')) {
    return null;
  }
  const asked = Number(typed);
  return asked >= 1 && asked <= totalPages ? asked : null;
}
