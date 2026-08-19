import { useLocation, useNavigate, useSearchParams } from 'react-router';

import { type SelectionChange, withSelection } from './selection';

/**
 * The selection's URL: what it currently says, and where a change to it leads.
 *
 * Both controls that scope the list write through here — the section's year and
 * ordering choosers, and the list's paging — so the re-page rule and the
 * fragment are honoured once rather than at each call site. `selection.ts` holds
 * the half that is only strings; this is the half that knows about the browser.
 */
export function useSelectionUrl() {
  const { pathname, hash } = useLocation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  /**
   * The whole location this section sits at, hash included: a fragment survives
   * a correction as well as a choice, which it would not through
   * `setSearchParams`.
   */
  function locationWith(params: URLSearchParams) {
    return { pathname, search: `?${params.toString()}`, hash };
  }

  function locationFor(change: SelectionChange) {
    return locationWith(withSelection(searchParams, change));
  }

  /**
   * A change a reader asked for, pushed: going back returns them to the
   * selection they came from. A correction is not one of these — it replaces,
   * being no choice of theirs — and is navigated to with `locationWith` at the
   * point that decides it.
   */
  function choose(change: SelectionChange) {
    void navigate(locationFor(change));
  }

  return { searchParams, locationWith, locationFor, choose };
}
