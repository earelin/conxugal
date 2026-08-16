import { Tabs } from '@mantine/core';
import type { ReactNode } from 'react';
import { Link } from 'react-router';

import type { Family } from './families';

interface FamilyTabsProps {
  organoId: string;
  /** The families this Órgano holds, in registry order. Never empty. */
  held: Family[];
  active: Family;
  children: ReactNode;
}

/**
 * The bar that says what the page holds, and the panel the active family's
 * section fills.
 *
 * Each tab is a link to that family's own route, so it opens in a new tab, can
 * be copied, and reaches the reader's history as one entry per family they
 * actually chose. The bar is the URL's mirror rather than its owner: which tab
 * is active is decided above, so a deep link, the back button and a click all
 * arrive the same way. A single tab still draws the full bar — the next family
 * joins it rather than changing the shape of this page.
 *
 * Arrow keys move focus without opening anything: opening a family is a
 * navigation that mounts a section and reads its contracts, so arrowing across
 * the bar would fetch every family in between and stack one history entry per
 * key press. Enter is what chooses.
 */
export function FamilyTabs({ organoId, held, active, children }: FamilyTabsProps) {
  return (
    <Tabs value={active.path} activateTabWithKeyboard={false}>
      <Tabs.List>
        {held.map((family) => (
          <Tabs.Tab
            key={family.key}
            value={family.path}
            renderRoot={(props) => <Link to={`/organo/${organoId}/${family.path}`} {...props} />}
          >
            {family.label}
          </Tabs.Tab>
        ))}
      </Tabs.List>
      <Tabs.Panel value={active.path} pt="md">
        {children}
      </Tabs.Panel>
    </Tabs>
  );
}
