// The keyboard contract of a list that offers options rather than links: one
// tab stop, the arrows moving within it, and a search box above it that hands
// focus down. Shared so every such list answers the same keys.
import type { KeyboardEvent, RefObject } from 'react';

const OPTION_SELECTOR = '[role="option"]';

/** Where a key moves focus within a list, or null when it is not ours. */
function nextIndex(key: string, current: number, last: number): number | null {
  switch (key) {
    case 'ArrowDown':
      return Math.min(current + 1, last);
    case 'ArrowUp':
      return Math.max(current - 1, 0);
    case 'Home':
      return 0;
    case 'End':
      return last;
    default:
      return null;
  }
}

function optionsOf(list: RefObject<HTMLElement | null>): HTMLElement[] {
  const element = list.current;
  return element === null ? [] : Array.from(element.querySelectorAll<HTMLElement>(OPTION_SELECTOR));
}

/** `onKeyDown` for the list itself: the arrows, Home and End walk the options. */
export function movesFocusWithin(list: RefObject<HTMLElement | null>) {
  return (event: KeyboardEvent<HTMLElement>) => {
    const options = optionsOf(list);
    if (options.length === 0) {
      return;
    }
    const focused = options.findIndex((option) => option === document.activeElement);
    const target = nextIndex(event.key, focused, options.length - 1);
    if (target === null) {
      return;
    }
    event.preventDefault();
    options[target].focus();
  };
}

/** `onKeyDown` for the search box above it: ArrowDown steps into the list. */
export function entersListFromSearch(list: RefObject<HTMLElement | null>) {
  return (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== 'ArrowDown') {
      return;
    }
    const [first] = optionsOf(list);
    if (first) {
      event.preventDefault();
      first.focus();
    }
  };
}
