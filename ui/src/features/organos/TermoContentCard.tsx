import { Breadcrumbs, Card, Divider, Group, Stack, Text, Title } from '@mantine/core';

import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';
import { OrganosTable } from './OrganosTable';
import { findTermoPath, type TermoNode } from './taxonomiaTree';

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
  countOne: string;
  countOther: string;
}

function unclassifiedPane(unclassified: Organo[]): Pane {
  return {
    title: strings.admin.organos.unclassified,
    subtitle: strings.admin.organos.unclassifiedSubtitle,
    trail: [{ key: 'sen-clasificar', label: strings.admin.organos.unclassified }],
    organos: unclassified,
    emptyMessage: strings.admin.organos.unclassifiedEmpty,
    countOne: strings.admin.organos.countUnclassifiedOne,
    countOther: strings.admin.organos.countUnclassifiedOther,
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
    countOne: strings.admin.organos.countInTermOne,
    countOther: strings.admin.organos.countInTermOther,
  };
}

interface TermoContentCardProps {
  roots: TermoNode[];
  unclassified: Organo[];
  selectedTermoId: string | null;
}

export function TermoContentCard({ roots, unclassified, selectedTermoId }: TermoContentCardProps) {
  const path = selectedTermoId === null ? [] : findTermoPath(roots, selectedTermoId);
  // A selected term can vanish under a concurrent delete; falling back to the
  // worklist keeps the pane populated instead of blanking it.
  const pane = path.length > 0 ? termoPane(path) : unclassifiedPane(unclassified);
  const count = pane.organos.length;

  return (
    <Card withBorder radius="md" padding="md">
      <Breadcrumbs separator="›" mb="sm">
        {[ROOT_CRUMB, ...pane.trail].map((crumb, index, all) => (
          <Text key={crumb.key} size="xs" c="dimmed" fw={index === all.length - 1 ? 600 : 400}>
            {crumb.label}
          </Text>
        ))}
      </Breadcrumbs>

      <Group justify="space-between" align="flex-start" wrap="nowrap">
        <Stack gap={0}>
          <Title order={3}>{pane.title}</Title>
          {pane.subtitle && (
            <Text size="sm" c="dimmed">
              {pane.subtitle}
            </Text>
          )}
        </Stack>
        {/* Pane action slot: rename, move, delete and assign land here. */}
        <Group gap="xs" />
      </Group>
      <Divider my="sm" />

      <OrganosTable organos={pane.organos} emptyMessage={pane.emptyMessage} label={pane.title} />

      {count > 0 && (
        <Text size="xs" c="dimmed" mt="sm">
          {count} {count === 1 ? pane.countOne : pane.countOther}
        </Text>
      )}
    </Card>
  );
}
