import { Tabs } from '@mantine/core';
import type { ComponentPropsWithoutRef, KeyboardEvent, ReactNode } from 'react';
import { Link } from 'react-router';

import { strings } from '../../shared/lib/strings';
import type { Family } from './families';

const copy = strings.organo;

interface FamilyTabsProps {
  /** `/organo/{id}`, which each family's segment hangs off. */
  basePath: string;
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
 * key press. Enter and Space are what choose.
 */
export function FamilyTabs({ basePath, held, active, children }: FamilyTabsProps) {
  return (
    <Tabs value={active.path} activateTabWithKeyboard={false}>
      <Tabs.List aria-label={copy.tabsLabel}>
        {held.map((family) => (
          <Tabs.Tab
            key={family.key}
            value={family.path}
            renderRoot={(props: TabRootProps) => (
              <FamilyTabLink {...props} to={`${basePath}/${family.path}`} />
            )}
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

type TabRootProps = ComponentPropsWithoutRef<'a'> & {
  /** `button`, which is what Mantine's tab would have been. On an anchor it
   * would claim the linked document's media type, so it is dropped. */
  type?: string;
};

/**
 * A tab as a link, answering Space as well as Enter. A `role="tab"` is expected
 * to activate on either, and an anchor answers only Enter — Space scrolls the
 * page instead, leaving the tab looking unresponsive to the one key a reader
 * arriving from the arrows is most likely to press.
 */
function FamilyTabLink({ type: _type, onKeyDown, to, ...props }: TabRootProps & { to: string }) {
  return (
    <Link
      {...props}
      to={to}
      onKeyDown={(event: KeyboardEvent<HTMLAnchorElement>) => {
        onKeyDown?.(event);
        if (event.key === ' ') {
          event.preventDefault();
          event.currentTarget.click();
        }
      }}
    />
  );
}
