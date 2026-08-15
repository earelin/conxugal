import type { KeyboardEvent } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { entersListFromSearch, movesFocusWithin } from './rovingFocus';

/** A list of focusable options, in the document so they can take focus. */
function listOf(count: number) {
  const list = document.createElement('div');
  for (let index = 0; index < count; index += 1) {
    const option = document.createElement('button');
    option.setAttribute('role', 'option');
    option.textContent = `option ${index}`;
    list.append(option);
  }
  document.body.append(list);
  return { list, ref: { current: list } };
}

function options(list: HTMLElement): HTMLElement[] {
  return Array.from(list.querySelectorAll<HTMLElement>('[role="option"]'));
}

/** A key press, carrying only what the handlers read off it. */
function press(key: string) {
  const preventDefault = vi.fn();
  return {
    event: { key, preventDefault } as unknown as KeyboardEvent<HTMLElement>,
    preventDefault,
  };
}

afterEach(() => {
  document.body.replaceChildren();
});

describe('movesFocusWithin', () => {
  it('steps down and up the options', () => {
    const { list, ref } = listOf(3);
    const move = movesFocusWithin(ref);
    options(list)[0].focus();

    move(press('ArrowDown').event);
    expect(document.activeElement).toBe(options(list)[1]);

    move(press('ArrowUp').event);
    expect(document.activeElement).toBe(options(list)[0]);
  });

  it('stops at each end rather than wrapping round', () => {
    const { list, ref } = listOf(2);
    const move = movesFocusWithin(ref);

    options(list)[0].focus();
    move(press('ArrowUp').event);
    expect(document.activeElement).toBe(options(list)[0]);

    options(list)[1].focus();
    move(press('ArrowDown').event);
    expect(document.activeElement).toBe(options(list)[1]);
  });

  it('jumps to the ends on Home and End', () => {
    const { list, ref } = listOf(4);
    const move = movesFocusWithin(ref);
    options(list)[1].focus();

    move(press('End').event);
    expect(document.activeElement).toBe(options(list)[3]);

    move(press('Home').event);
    expect(document.activeElement).toBe(options(list)[0]);
  });

  it('lands on the first option when focus was not in the list at all', () => {
    // The list itself holds the tab stop, so the first key press arrives with
    // focus still on the container rather than on any option.
    const { list, ref } = listOf(3);

    movesFocusWithin(ref)(press('ArrowDown').event);

    expect(document.activeElement).toBe(options(list)[0]);
  });

  it('leaves a key it does not answer to whatever else wants it', () => {
    const { list, ref } = listOf(2);
    options(list)[0].focus();
    const typed = press('a');

    movesFocusWithin(ref)(typed.event);

    expect(document.activeElement).toBe(options(list)[0]);
    expect(typed.preventDefault).not.toHaveBeenCalled();
  });

  it('does nothing for a list holding no options, and claims no key', () => {
    const { ref } = listOf(0);
    const down = press('ArrowDown');

    movesFocusWithin(ref)(down.event);

    expect(down.preventDefault).not.toHaveBeenCalled();
  });

  it('does nothing before the list it was given has been rendered', () => {
    const down = press('ArrowDown');

    movesFocusWithin({ current: null })(down.event);

    expect(down.preventDefault).not.toHaveBeenCalled();
  });
});

describe('entersListFromSearch', () => {
  it('steps into the list on ArrowDown, taking the key with it', () => {
    const { list, ref } = listOf(3);
    const down = press('ArrowDown');

    entersListFromSearch(ref)(down.event);

    expect(document.activeElement).toBe(options(list)[0]);
    expect(down.preventDefault).toHaveBeenCalledOnce();
  });

  it('leaves every other key to the search box, ArrowUp included', () => {
    const { list, ref } = listOf(3);
    const up = press('ArrowUp');

    entersListFromSearch(ref)(up.event);

    expect(document.activeElement).not.toBe(options(list)[0]);
    expect(up.preventDefault).not.toHaveBeenCalled();
  });

  it('leaves the reader in the search box when the list offers nothing', () => {
    // A query matching nothing: ArrowDown has nowhere to go and must not eat
    // the key on its way to finding that out.
    const { ref } = listOf(0);
    const down = press('ArrowDown');

    entersListFromSearch(ref)(down.event);

    expect(down.preventDefault).not.toHaveBeenCalled();
  });
});
