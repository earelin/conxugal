import {
  Badge,
  Box,
  Button,
  Card,
  Divider,
  getTreeExpandedState,
  Group,
  NavLink,
  type RenderTreeNodePayload,
  Text,
  Tree,
  type TreeNodeData,
  useTree,
} from '@mantine/core';
import { IconChevronDown, IconChevronRight, IconInbox, IconPlus } from '@tabler/icons-react';
import { type KeyboardEvent, useMemo, useState } from 'react';

import { strings } from '../../shared/lib/strings';
import { CreateTermoModal } from './CreateTermoModal';
import type { Organo, Termo } from './organos';
import { findTermoPath, type TermoNode } from './taxonomiaTree';
import { type TermoActionHandlers, TermoActionIcons } from './TermoActionControls';

function toTreeData(nodes: TermoNode[]): TreeNodeData[] {
  return nodes.map((node) => ({
    value: node.id,
    label: node.name,
    children: node.children.length > 0 ? toTreeData(node.children) : undefined,
  }));
}

function collectCounts(nodes: TermoNode[], counts: Map<string, number>): Map<string, number> {
  for (const node of nodes) {
    counts.set(node.id, node.organos.length);
    collectCounts(node.children, counts);
  }
  return counts;
}

// Chevron and leaf spacer share this width, which is what lines labels up
// across a level whether or not the term has children.
const MARKER_SIZE = 14;

function ExpandMarker({ hasChildren, expanded }: { hasChildren: boolean; expanded: boolean }) {
  if (!hasChildren) {
    return <Box w={MARKER_SIZE} />;
  }
  const Chevron = expanded ? IconChevronDown : IconChevronRight;
  return <Chevron size={MARKER_SIZE} color="var(--mantine-color-gray-6)" aria-hidden />;
}

interface TermoRowProps {
  payload: RenderTreeNodePayload;
  count: number;
  actions: TermoActionHandlers;
}

function TermoRow({ payload, count, actions }: TermoRowProps) {
  const { node, selected, expanded, hasChildren, elementProps } = payload;

  return (
    <Group {...elementProps} gap="xs" justify="space-between" wrap="nowrap" py={4}>
      <Group gap={6} wrap="nowrap">
        <ExpandMarker hasChildren={hasChildren} expanded={expanded} />
        <Text size="sm" fw={selected ? 600 : 500} c={selected ? 'indigo.8' : undefined}>
          {node.label}
        </Text>
      </Group>
      {/* Never shrink: a long term name wraps to two lines at a narrow viewport
          and would otherwise squeeze the count away to an empty pill. */}
      <Group gap={2} wrap="nowrap" style={{ flexShrink: 0 }}>
        {/* The open term trades its count for its actions rather than adding to
            the row: three icons plus a badge do not fit beside a term name at
            360 px, and the count is on screen in the content pane anyway. */}
        {selected ? (
          <TermoActionIcons {...actions} />
        ) : (
          <Badge variant="light" color="gray" size="sm">
            {count}
          </Badge>
        )}
      </Group>
    </Group>
  );
}

interface TaxonomiaTreeCardProps {
  roots: TermoNode[];
  unclassified: Organo[];
  selectedTermoId: string | null;
  onSelect: (termoId: string | null) => void;
  termoActions: TermoActionHandlers;
}

export function TaxonomiaTreeCard({
  roots,
  unclassified,
  selectedTermoId,
  onSelect,
  termoActions,
}: TaxonomiaTreeCardProps) {
  const [creating, setCreating] = useState(false);
  const data = useMemo(() => toTreeData(roots), [roots]);
  const counts = useMemo(() => collectCounts(roots, new Map()), [roots]);
  // A fresh array literal here would bust useTree's own memo on every render of
  // the page and re-render every node.
  const selectedState = useMemo(
    () => (selectedTermoId === null ? [] : [selectedTermoId]),
    [selectedTermoId],
  );

  const tree = useTree({
    selectedState,
    initialExpandedState: getTreeExpandedState(data, '*'),
    onSelectedStateChange: (state) => {
      if (state.length > 0) {
        onSelect(state[0]);
      }
    },
  });

  // Mantine wires selection to click alone: its own key handler covers the
  // arrows and Space (which expands), and there is no Enter branch at all, so
  // without this a keyboard or screen-reader user can walk the taxonomía but
  // never open a term. The handler sits on the tree root because the key event
  // bubbles from the focused `treeitem`, which carries the node's value.
  function selectFocusedNodeOnEnter(event: KeyboardEvent<HTMLUListElement>) {
    if (event.key !== 'Enter') {
      return;
    }
    const { value } = (event.target as HTMLElement).dataset;
    if (value !== undefined) {
      event.preventDefault();
      onSelect(value);
    }
  }

  // The tree instance is this component's, which is why the create dialog lives
  // here too: a term created under a parent the administrator had collapsed
  // would otherwise be selected but invisible.
  function selectCreated(created: Termo) {
    setCreating(false);
    if (created.parentId !== null) {
      for (const ancestor of findTermoPath(roots, created.parentId)) {
        tree.expand(ancestor.id);
      }
    }
    onSelect(created.id);
  }

  return (
    <Card withBorder radius="md" padding="md">
      <Group justify="space-between" mb="xs">
        <Text fw={600}>{strings.admin.organos.treeTitle}</Text>
        <Group gap="xs">
          <Button size="xs" leftSection={<IconPlus size={14} />} onClick={() => setCreating(true)}>
            {strings.admin.organos.termo.create}
          </Button>
        </Group>
      </Group>
      <Divider mb="xs" />

      <CreateTermoModal
        opened={creating}
        roots={roots}
        defaultParentId={selectedTermoId}
        onCreated={selectCreated}
        onCancel={() => setCreating(false)}
      />

      {roots.length === 0 ? (
        <Text c="dimmed" size="sm" mb="xs">
          {strings.admin.organos.treeEmpty}
        </Text>
      ) : (
        <Tree
          data={data}
          tree={tree}
          levelOffset="md"
          selectOnClick
          // One term at a time: range selection would emit multi-value states
          // this section has no meaning for.
          allowRangeSelection={false}
          aria-label={strings.admin.organos.treeTitle}
          onKeyDown={selectFocusedNodeOnEnter}
          renderNode={(payload) => (
            <TermoRow
              payload={payload}
              count={counts.get(payload.node.value) ?? 0}
              actions={termoActions}
            />
          )}
        />
      )}

      <Divider my="xs" />
      <NavLink
        component="button"
        type="button"
        active={selectedTermoId === null}
        // The tree items carry aria-selected; this row is the other half of the
        // same single-selection group, so its state has to reach the a11y tree too.
        aria-current={selectedTermoId === null}
        onClick={() => onSelect(null)}
        leftSection={<IconInbox size={16} />}
        label={strings.admin.organos.unclassified}
        rightSection={
          <Badge variant="light" color="orange" size="sm">
            {unclassified.length}
          </Badge>
        }
      />
    </Card>
  );
}
