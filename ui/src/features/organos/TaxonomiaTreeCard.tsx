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

// The tree data Mantine renders carries only a value and a label, so a row that
// needs anything else about its term — its count, or its name as a string
// rather than a ReactNode — looks it up here by id.
function indexById(nodes: TermoNode[], byId: Map<string, TermoNode>): Map<string, TermoNode> {
  for (const node of nodes) {
    byId.set(node.id, node);
    indexById(node.children, byId);
  }
  return byId;
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
  termo: TermoNode | undefined;
  actions: TermoActionHandlers;
}

function TermoRow({ payload, termo, actions }: TermoRowProps) {
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
        {selected && termo ? (
          <TermoActionIcons termoName={termo.name} {...actions} />
        ) : (
          <Badge variant="light" color="gray" size="sm">
            {termo?.organos.length ?? 0}
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
  const termosById = useMemo(() => indexById(roots, new Map()), [roots]);
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
  //
  // One write, not a call per ancestor: Mantine's `expand` spreads the expanded
  // state its closure captured, so a loop of them within a batch keeps only the
  // last — which would leave every ancestor above the immediate parent shut, and
  // the new term still out of sight.
  function selectCreated(created: Termo) {
    setCreating(false);
    if (created.parentId !== null) {
      const ancestors = findTermoPath(roots, created.parentId);
      tree.setExpandedState({
        ...tree.expandedState,
        ...Object.fromEntries(ancestors.map((ancestor) => [ancestor.id, true])),
      });
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
              termo={termosById.get(payload.node.value)}
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
