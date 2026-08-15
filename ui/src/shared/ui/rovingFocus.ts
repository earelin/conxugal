/**
 * A list of options is one tab stop, and the arrows move within it. Shared by
 * every list that offers options rather than links, so they answer the same
 * keys.
 */
const OPTION_SELECTOR = '[role="option"]';

/** Where a key moves focus within a list, or null when it is not ours. */
export function nextIndex(key: string, current: number, last: number): number | null {
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

export function optionsOf(list: HTMLElement | null): HTMLElement[] {
  return list === null ? [] : Array.from(list.querySelectorAll<HTMLElement>(OPTION_SELECTOR));
}
