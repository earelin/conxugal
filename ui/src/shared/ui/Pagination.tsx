import { Box, Button, Divider, Group, Input, Text } from '@mantine/core';
import {
  IconChevronLeft,
  IconChevronLeftPipe,
  IconChevronRight,
  IconChevronRightPipe,
} from '@tabler/icons-react';
import { type ChangeEvent, type KeyboardEvent, useId, useState } from 'react';

import { formatCount } from '../lib/number';
import { counted } from '../lib/plural';
import { strings } from '../lib/strings';

const copy = strings.pagination;

/**
 * Decimal digits only. `Number` would otherwise read a page number in another
 * base or in exponent form — `0x10` as 16, `1e1` as 10 — and page somewhere the
 * reader did not ask for, which is the one thing the jump must not do.
 */
const DIGITS = /^\d+$/;

interface PaginationProps {
  /**
   * The page being shown, 1-based, exactly as the wire states it. Callers are
   * expected to have clamped it to `1…totalPages`: a page past the end leaves
   * *last* disabled, since it is already at or beyond the end it would go to.
   */
  page: number;
  /**
   * The page size the wire states. Read by nobody here: both totals below are
   * served, so the control never divides by this to find a page count. It is
   * taken all the same because the envelope carries it, and a caller handing
   * over three of four fields would have to know which one is spare.
   */
  size: number;
  /** How many entries the whole selection holds, not how many this page shows. */
  totalItems: number;
  /** Pages at this size, served rather than computed. Zero when nothing matched. */
  totalPages: number;
  /** Takes the 1-based page named by whichever control was used. */
  onPageChange: (page: number) => void;
}

/**
 * The one paging control, for every paginated list in the system.
 *
 * It renders the four numbers of the paged envelope in the base they arrive in
 * — there is no arithmetic between the wire and the screen — so the number a
 * URL carries, the number the API takes and the number shown here are one
 * number, and no list that takes this control can page differently from the
 * rest. That sameness is the whole reason it lives here and knows nothing about
 * what it is paging.
 *
 * At the two ends the controls that would lead nowhere are disabled rather than
 * removed, so the control never changes shape under a reader mid-session. A
 * single page still draws all of it: how many entries the selection holds is an
 * answer worth giving whether or not there is anywhere to page to.
 */
export function Pagination({ page, totalItems, totalPages, onPageChange }: PaginationProps) {
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

  const atStart = page <= 1;
  const atEnd = page >= totalPages;
  const single = totalPages <= 1;

  function commit() {
    // A blank box, anything that is not a run of digits, and a page outside the
    // selection are all refused rather than corrected: silently paging
    // somewhere adjacent to what was asked for is worse than not moving at all.
    const asked = DIGITS.test(typed?.trim() ?? '') ? Number(typed) : NaN;
    if (asked >= 1 && asked <= totalPages) {
      onPageChange(asked);
    }
    // Either way the box goes back to speaking for the page prop, which is how
    // a refusal shows itself: the number typed does not stay.
    setTyped(null);
  }

  return (
    <Box component="nav" aria-label={copy.navLabel}>
      <Divider color="gray.1" />
      <Group justify="space-between" gap="sm" wrap="wrap" pt="sm">
        <Text size="sm" c="dimmed">
          {counted(totalItems, copy.records)}
        </Text>
        <Group gap="xs" wrap="wrap">
          <Button
            variant="default"
            size="sm"
            disabled={atStart}
            leftSection={<IconChevronLeftPipe size={16} />}
            onClick={() => onPageChange(1)}
          >
            {copy.first}
          </Button>
          <Button
            variant="default"
            size="sm"
            disabled={atStart}
            leftSection={<IconChevronLeft size={16} />}
            onClick={() => onPageChange(page - 1)}
          >
            {copy.previous}
          </Button>
          <Group gap="xs" wrap="nowrap">
            {/* Gives the word a click target on the box; the name comes from `aria-labelledby` below. */}
            <Text component="label" id={labelId} htmlFor={jumpId} size="sm" c="dimmed">
              {copy.pageLabel}
            </Text>
            {/*
             * `Input`, not `TextInput`: the label and the total are laid out here
             * by hand, so none of `TextInput`'s wrapper is wanted.
             *
             * The page total is named rather than described because Mantine's
             * input family always sets `aria-describedby` from its wrapper
             * context — a value passed in is overwritten with `undefined` and
             * the bound would be lost in silence. Naming the box for both
             * states the range at the moment a page is being typed into it,
             * which is when a reader needs it.
             */}
            <Input
              id={jumpId}
              size="sm"
              w={64}
              inputMode="numeric"
              aria-labelledby={`${labelId} ${totalId}`}
              disabled={single}
              value={typed ?? String(page)}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setTyped(event.currentTarget.value)
              }
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
          <Button
            variant="default"
            size="sm"
            disabled={atEnd}
            rightSection={<IconChevronRight size={16} />}
            onClick={() => onPageChange(page + 1)}
          >
            {copy.next}
          </Button>
          <Button
            variant="default"
            size="sm"
            disabled={atEnd}
            rightSection={<IconChevronRightPipe size={16} />}
            onClick={() => onPageChange(totalPages)}
          >
            {copy.last}
          </Button>
        </Group>
      </Group>
    </Box>
  );
}
