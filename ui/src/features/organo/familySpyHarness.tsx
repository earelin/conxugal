import { useOutletContext } from 'react-router';

import type { OrganoOutletContext } from '../../shared/entities/organo';

/**
 * Stands in for the contract family's section, which arrives as a child route of
 * its own. It reads the summary the page hands it exactly as that section will:
 * out of the outlet context, with no request of its own.
 *
 * Alone in its file because it is the only component the organo harness needs:
 * a module mixing one with the fixtures and render helpers around it is what
 * `react-refresh/only-export-components` is there to catch.
 */
export function FamilySectionSpy() {
  const { organo, family } = useOutletContext<OrganoOutletContext>();
  const summary = family.summary as { years: number[] };
  return <p>{`${organo.name} abre en ${summary.years[0]}`}</p>;
}
