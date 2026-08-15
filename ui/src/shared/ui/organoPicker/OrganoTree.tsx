import {
  Box,
  getTreeExpandedState,
  Group,
  type RenderTreeNodePayload,
  ScrollArea,
  Text,
  Tree,
  type TreeNodeData,
  useTree,
} from '@mantine/core';
import { IconCheck, IconChevronDown, IconChevronRight } from '@tabler/icons-react';
import { type KeyboardEvent, type MouseEvent, useMemo } from 'react';

import { SELECTED_ROW_BG, SELECTED_ROW_COLOR } from '../selection';
import { MARKER_SIZE, MAX_BODY_HEIGHT } from './dimensions';
import { isTermo, ORGANO } from './treeData';

function ExpandMarker({ hasChildren, expanded }: { hasChildren: boolean; expanded: boolean }) {
  if (!hasChildren) {
    return <Box w={MARKER_SIZE} />;
  }
  const Chevron = expanded ? IconChevronDown : IconChevronRight;
  return <Chevron size={MARKER_SIZE} color="var(--mantine-color-gray-6)" aria-hidden />;
}

interface PickerRowProps {
  payload: RenderTreeNodePayload;
  onOpen: (value: string) => void;
}

function PickerRow({ payload, onOpen }: PickerRowProps) {
  const { node, expanded, hasChildren, selected, elementProps } = payload;
  const termo = isTermo(node.value);

  return (
    <Group
      {...elementProps}
      // Mantine's own handler expands the branch; opening the Órgano is ours.
      onClick={(event: MouseEvent<HTMLDivElement>) => {
        elementProps.onClick(event);
        onOpen(node.value);
      }}
      gap={6}
      wrap="nowrap"
      // Tall enough to read, short enough that its middle meets the elbow of
      // the guide line, which Mantine pins at a fixed 12 px down the row.
      py={2}
      // Only the end side: the row's start padding is the indent, which
      // Mantine's own label class takes from the level it set on the `li`. Any
      // padding prop covering the start side — `px`, `p`, `pl`, `ps` — writes
      // over it and flattens the tree into a single column, and the guide
      // lines are drawn expecting a row to begin exactly at that offset.
      pe={6}
      bg={selected ? SELECTED_ROW_BG : undefined}
      // On the row rather than the label, so the check inherits it too — the
      // chevron sets its own colour and is unaffected.
      c={selected ? SELECTED_ROW_COLOR : undefined}
      style={{ ...elementProps.style, borderRadius: 'var(--mantine-radius-sm)' }}
    >
      <ExpandMarker hasChildren={hasChildren} expanded={expanded} />
      <Text size="sm" fw={termo ? 500 : 400}>
        {node.label}
      </Text>
      {selected && <IconCheck size={MARKER_SIZE} aria-hidden />}
    </Group>
  );
}

interface OrganoTreeProps {
  data: TreeNodeData[];
  /** The Órgano the route has open, marked as selected. */
  openId: string | null;
  onOpen: (value: string) => void;
  labelledBy: string;
}

/**
 * The tree itself, rendered only once there is something to draw and remounted
 * whenever the taxonomía changes shape: `useTree` fixes its expanded state when
 * it is created and defaults nodes it has not seen to collapsed, so a longer
 * lived instance would show a term that arrived later shut inside an otherwise
 * open tree.
 *
 * Selection here is presentational — the route says which Órgano is open, and
 * nothing in the tree writes back to it. Opening one runs off the row's own
 * click and the Enter key instead, because Mantine's keyboard handler applies
 * range selection whether or not `allowRangeSelection` is set, and a range
 * would otherwise navigate to whichever Órgano happened to head it.
 */
export function OrganoTree({ data, openId, onOpen, labelledBy }: OrganoTreeProps) {
  const selectedState = useMemo(() => (openId === null ? [] : [`${ORGANO}${openId}`]), [openId]);
  const initialExpandedState = useMemo(() => getTreeExpandedState(data, '*'), [data]);

  const tree = useTree({ selectedState, initialExpandedState });

  // Mantine's own key handler covers the arrows and Space; there is no Enter
  // branch at all, so without this a keyboard user can walk the tree but never
  // act on it. The event bubbles from the focused `treeitem`, which carries the
  // node's value.
  function actOnFocusedNodeOnEnter(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key !== 'Enter') {
      return;
    }
    const { value } = (event.target as HTMLElement).dataset;
    if (value === undefined) {
      return;
    }
    event.preventDefault();
    if (isTermo(value)) {
      tree.toggleExpanded(value);
    } else {
      onOpen(value);
    }
  }

  return (
    // Every branch opens at once, so a large visible set draws a tree taller
    // than the window it drops into.
    <ScrollArea.Autosize mah={MAX_BODY_HEIGHT} type="auto">
      <Tree
        data={data}
        tree={tree}
        levelOffset="md"
        // The indent alone is a thin signal in a narrow dropdown holding names
        // long enough to wrap; the lines say which branch a row hangs off even
        // when its label runs over two rows.
        withLines
        // Inset the whole tree rather than each row: a row's own start padding
        // is its indent, and the guide lines are positioned within the row, so
        // both shift together and stay aligned with the chevron above them.
        ps={6}
        aria-labelledby={labelledBy}
        onKeyDown={actOnFocusedNodeOnEnter}
        renderNode={(payload) => <PickerRow payload={payload} onOpen={onOpen} />}
      />
    </ScrollArea.Autosize>
  );
}
