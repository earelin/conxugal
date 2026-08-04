import { Box, Breadcrumbs, Card, Divider, Group, Stack, Text, Title } from '@mantine/core';
import { useState } from 'react';

import { singularOrPlural, type Word } from '../../shared/lib/plural';
import { strings } from '../../shared/lib/strings';
import { useClearOrgano } from './organoMutations';
import { placementRefusal } from './organoRefusal';
import type { Organo } from './organos';
import { OrganosTable } from './OrganosTable';
import type { TermoNode } from './taxonomiaTree';
import { TermoActionButtons, type TermoActionHandlers } from './TermoActionControls';
import type { Refusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';

// Term names repeat across levels — nothing stops a term carrying its
// ancestor's name — so each crumb keeps the id it came from as its key.
interface Crumb {
  key: string;
  label: string;
}

const ROOT_CRUMB: Crumb = { key: 'taxonomia', label: strings.admin.organos.treeTitle };

interface Pane {
  title: string;
  subtitle: string | null;
  trail: Crumb[];
  organos: Organo[];
  emptyMessage: string;
  countWord: Word;
}

function unclassifiedPane(unclassified: Organo[]): Pane {
  return {
    title: strings.admin.organos.unclassified,
    subtitle: strings.admin.organos.unclassifiedSubtitle,
    trail: [{ key: 'sen-clasificar', label: strings.admin.organos.unclassified }],
    organos: unclassified,
    emptyMessage: strings.admin.organos.unclassifiedEmpty,
    countWord: strings.admin.organos.countUnclassified,
  };
}

function termoPane(path: TermoNode[]): Pane {
  const termo = path[path.length - 1];
  return {
    title: termo.name,
    subtitle: null,
    trail: path.map((node) => ({ key: node.id, label: node.name })),
    organos: termo.organos,
    emptyMessage: strings.admin.organos.termEmpty,
    countWord: strings.admin.organos.countInTerm,
  };
}

interface TermoContentCardProps {
  /** Root-to-term chain of the open term; empty selects the worklist. */
  openPath: TermoNode[];
  unclassified: Organo[];
  termoActions: TermoActionHandlers;
  onAssignOrgano: (organo: Organo) => void;
  /** Re-reads the section, which is the only way past a stale-record refusal. */
  onRefresh: () => void;
}

export function TermoContentCard({
  openPath,
  unclassified,
  termoActions,
  onAssignOrgano,
  onRefresh,
}: TermoContentCardProps) {
  const pane = openPath.length > 0 ? termoPane(openPath) : unclassifiedPane(unclassified);
  const count = pane.organos.length;
  const clearOrgano = useClearOrgano();
  const [clearRefusal, setClearRefusal] = useState<Refusal | null>(null);

  function refresh() {
    // The refusal asked for a re-read; leaving it up afterwards would keep
    // asking for one that has already happened, with no other way to dismiss it.
    setClearRefusal(null);
    onRefresh();
  }

  // A clear needs no dialog: there is nothing to choose and nothing to confirm,
  // since the Órgano returns to the worklist rather than being deleted. So it
  // runs from the row and reports here, above the table it changes.
  const rowActions = {
    onAssign: onAssignOrgano,
    onClear:
      openPath.length > 0
        ? (organo: Organo) => {
            setClearRefusal(null);
            clearOrgano.mutate(organo.id, {
              onError: (error) => setClearRefusal(placementRefusal(error)),
            });
          }
        : undefined,
    clearingId: clearOrgano.isPending ? clearOrgano.variables : undefined,
  };

  return (
    <Card withBorder radius="md" padding="md">
      <Breadcrumbs separator="›" mb="sm">
        {[ROOT_CRUMB, ...pane.trail].map((crumb, index, all) => (
          <Text key={crumb.key} size="xs" c="dimmed" fw={index === all.length - 1 ? 600 : 400}>
            {crumb.label}
          </Text>
        ))}
      </Breadcrumbs>

      {/* Wraps rather than holding one line: at 360 px the four term actions
          do not fit beside a term name, and they drop under it instead of
          pushing the card into a horizontal scroll. */}
      <Group justify="space-between" align="flex-start">
        <Stack gap={0}>
          <Title order={3}>{pane.title}</Title>
          {pane.subtitle && (
            <Text size="sm" c="dimmed">
              {pane.subtitle}
            </Text>
          )}
        </Stack>
        {/* The worklist is not a term: it has no name to change, no place in the
            tree and nothing to delete. Assign sits alongside them. */}
        {openPath.length > 0 && <TermoActionButtons {...termoActions} />}
      </Group>
      <Divider my="sm" />

      {clearRefusal && (
        <Box mb="sm">
          <TermoRefusalAlert refusal={clearRefusal} onRefresh={refresh} />
        </Box>
      )}

      <OrganosTable
        organos={pane.organos}
        emptyMessage={pane.emptyMessage}
        label={pane.title}
        actions={rowActions}
      />

      {count > 0 && (
        <Text size="xs" c="dimmed" mt="sm">
          {count} {singularOrPlural(count, pane.countWord)}
        </Text>
      )}
    </Card>
  );
}
