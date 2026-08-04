/**
 * The refetch react-query runs when the window regains focus, which is how a
 * test says *the records changed under an open dialog* without a control to
 * click: a section that renders no refresh of its own still re-reads on every
 * focus, and that is the read a stranded dialog would come back on.
 *
 * The event goes to `window` because that is where query-core registers its
 * listener; a browser fires it at `document` and lets it propagate. It also
 * depends on `refetchOnWindowFocus` and `staleTime` staying at their defaults —
 * `createQueryClient` overrides neither, and giving either one a value would
 * strand every caller on a timeout with nothing pointing back here.
 */
export function refocusWindow(): void {
  window.dispatchEvent(new Event('visibilitychange'));
}
