import { strings } from '../../shared/lib/strings';

export interface Family {
  /** The key this family appears under in the member read's `families`. */
  key: string;
  /** The child-route segment beneath `/organo/:id`, and the tab's value. */
  path: string;
  label: string;
}

/**
 * The contract families this build can draw a tab for and route to.
 *
 * A list rather than a map, so *the first family* means something deterministic
 * — it is what the bare path redirects to. Key, label and route segment travel
 * together because the tab bar renders all three: the label on the tab, the key
 * to find the family in the read, the segment to address it.
 *
 * A new family adds an entry here and a child route in `app/router.tsx`.
 */
export const FAMILIES: readonly Family[] = [
  {
    key: 'contratosMenores',
    path: 'contratos-menores',
    label: strings.organo.families.contratosMenores,
  },
];

/**
 * The registry entries the read holds data for, in registry order.
 *
 * A key this build does not know is ignored rather than drawn, so a server that
 * learns a family first draws no tab this router cannot follow.
 */
export function familiesHeld(families: Record<string, unknown>): Family[] {
  return FAMILIES.filter((family) => Object.hasOwn(families, family.key));
}
