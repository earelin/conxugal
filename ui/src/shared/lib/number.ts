// The backtracking sonarjs warns about is bounded here: this only ever matches
// against a rounded Number's own decimal form, never longer than ~21 characters.
// eslint-disable-next-line sonarjs/super-linear-regex
const thousandsBoundary = /\B(?=(\d{3})+(?!\d))/g;

export function formatCount(n: number): string {
  return Math.round(n).toString().replace(thousandsBoundary, ' ');
}
