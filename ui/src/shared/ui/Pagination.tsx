import { Box, Button, Divider, Group, Text } from '@mantine/core';
import {
  IconChevronLeft,
  IconChevronLeftPipe,
  IconChevronRight,
  IconChevronRightPipe,
} from '@tabler/icons-react';

import { counted } from '../lib/plural';
import { strings } from '../lib/strings';
import { PageJump } from './PageJump';

const copy = strings.pagination;

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
  const atStart = page <= 1;
  const atEnd = page >= totalPages;
  const single = totalPages <= 1;

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
          <PageJump
            page={page}
            totalPages={totalPages}
            disabled={single}
            onPageChange={onPageChange}
          />
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
