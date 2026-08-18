import { Group, Input, Text } from '@mantine/core';
import { type ChangeEvent, type KeyboardEvent, useId, useState } from 'react';

import { formatCount } from '../lib/number';
import { strings } from '../lib/strings';
import { askedPage } from './askedPage';

const copy = strings.pagination;

interface PageJumpProps {
  /** The page in force, which the box shows whenever nothing is being typed. */
  page: number;
  /** The upper bound a typed page has to fall inside, and the total on show. */
  totalPages: number;
  disabled: boolean;
  onPageChange: (page: number) => void;
}

/**
 * The jump to a chosen page: a box, the word that labels it, and the total that
 * bounds it.
 *
 * Enter commits and blur reverts, so focus leaving the box never navigates. A
 * page outside `1…totalPages` is refused rather than clamped to a neighbour —
 * paging somewhere adjacent to what was asked for is worse than not moving.
 */
export function PageJump({ page, totalPages, disabled, onPageChange }: PageJumpProps) {
  const jumpId = useId();
  const labelId = useId();
  const totalId = useId();
  /**
   * What the reader has typed but not yet committed, or `null` while they have
   * typed nothing. Keeping the untouched case as `null` — rather than seeding
   * state from `page` — is what lets the box follow the page prop when a button
   * moves it, with no effect syncing one into the other.
   */
  const [typed, setTyped] = useState<string | null>(null);

  function commit() {
    const asked = askedPage(typed, totalPages);
    if (asked !== null) {
      onPageChange(asked);
    }
    // Committed or refused, the box goes back to speaking for the page prop —
    // which is how a refusal shows itself: the number typed does not stay.
    setTyped(null);
  }

  return (
    <Group gap="xs" wrap="nowrap">
      {/* Gives the word a click target on the box; the name comes from `aria-labelledby` below. */}
      <Text component="label" id={labelId} htmlFor={jumpId} size="sm" c="dimmed">
        {copy.pageLabel}
      </Text>
      {/*
       * `Input`, not `TextInput`: the label and the total are laid out here by
       * hand, so none of `TextInput`'s wrapper is wanted.
       *
       * The page total is named rather than described because Mantine's input
       * family always sets `aria-describedby` from its wrapper context — a value
       * passed in is overwritten with `undefined` and the bound would be lost in
       * silence. Naming the box for both states the range at the moment a page
       * is being typed into it, which is when a reader needs it.
       */}
      <Input
        id={jumpId}
        size="sm"
        w={64}
        inputMode="numeric"
        aria-labelledby={`${labelId} ${totalId}`}
        disabled={disabled}
        value={typed ?? String(page)}
        onChange={(event: ChangeEvent<HTMLInputElement>) => setTyped(event.currentTarget.value)}
        onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            commit();
          }
        }}
        onBlur={() => setTyped(null)}
      />
      <Text id={totalId} size="sm" c="dimmed">
        {copy.ofPages(formatCount(totalPages))}
      </Text>
    </Group>
  );
}
