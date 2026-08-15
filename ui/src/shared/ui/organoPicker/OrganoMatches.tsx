import { Badge, Box, Group, ScrollArea, Stack, Text, UnstyledButton } from '@mantine/core';
import type { RefObject } from 'react';

import { strings } from '../../lib/strings';
import type { Organo } from '../../lib/taxonomiaTree';
import { movesFocusWithin } from '../rovingFocus';
import { SELECTED_ROW_BG, SELECTED_ROW_COLOR } from '../selection';
import { MAX_BODY_HEIGHT } from './dimensions';

const copy = strings.organoPicker;

interface OrganoMatchesProps {
  id: string;
  organos: Organo[];
  /**
   * What was typed, trimmed, and empty while the tree is the answer instead —
   * which is the whole of what says which of the two states this is in.
   */
  query: string;
  /** The Órgano the route has open, marked as selected here as in the tree. */
  openId: string | null;
  onOpen: (id: string) => void;
  labelledBy: string;
  listRef: RefObject<HTMLDivElement | null>;
}

/**
 * The filter's answer: a flat list of the Órganos whose names hold the query,
 * or the refusal when none do. It offers no paging, no sorting and no count,
 * and it never falls back to listing the catalogue when it has nothing to say.
 */
export function OrganoMatches({
  id,
  organos,
  query,
  openId,
  onOpen,
  labelledBy,
  listRef,
}: OrganoMatchesProps) {
  const searching = query !== '';
  // One tab stop, landing on the open Órgano when the query still offers it —
  // derived rather than held, so the filter changing the rows cannot leave a
  // stored index pointing at a row that is gone.
  const tabbableId = organos.find((organo) => organo.id === openId)?.id ?? organos[0]?.id;

  return (
    <>
      <Box style={{ display: searching && organos.length > 0 ? undefined : 'none' }}>
        <ScrollArea.Autosize mah={MAX_BODY_HEIGHT} type="auto">
          <Box
            ref={listRef}
            id={id}
            role="listbox"
            aria-labelledby={labelledBy}
            tabIndex={-1}
            onKeyDown={movesFocusWithin(listRef)}
          >
            {organos.map((organo) => {
              const selected = organo.id === openId;
              return (
                <UnstyledButton
                  key={organo.id}
                  type="button"
                  role="option"
                  aria-selected={selected}
                  tabIndex={organo.id === tabbableId ? 0 : -1}
                  onClick={() => {
                    onOpen(organo.id);
                  }}
                  display="block"
                  w="100%"
                  py={4}
                  px={6}
                  bg={selected ? SELECTED_ROW_BG : undefined}
                  style={{ borderRadius: 'var(--mantine-radius-sm)' }}
                >
                  <Group gap="xs" wrap="nowrap" justify="space-between">
                    <Text size="sm" c={selected ? SELECTED_ROW_COLOR : undefined}>
                      {organo.name}
                    </Text>
                    {/* The marker carries the state on its own: dimming the name
                        as well would say nothing to a screen reader and would
                        drop the row under the contrast the rest of the list
                        keeps. Inactive is a fact about the catalogue, not about
                        whether there is anything to see, so the row stays
                        selectable. */}
                    {!organo.active && (
                      <Badge color="gray" variant="light" size="xs">
                        {copy.inactive}
                      </Badge>
                    )}
                  </Group>
                </UnstyledButton>
              );
            })}
          </Box>
        </ScrollArea.Autosize>
      </Box>
      {/* Always mounted, and never the hidden half of a swap, so a screen
          reader hears the filter come up empty rather than nothing at all. */}
      <Box role="status">
        {searching && organos.length === 0 && (
          <Stack gap={4}>
            <Text size="sm">{copy.noMatches(query)}</Text>
            <Text size="xs" c="dimmed">
              {copy.noMatchesHelp}
            </Text>
          </Stack>
        )}
      </Box>
    </>
  );
}
