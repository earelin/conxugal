import { Breadcrumbs, Card, Divider, Group, Stack, Text, Title } from '@mantine/core';

import { strings } from '../../shared/lib/strings';
import type { Organo } from './organos';
import { OrganosTable } from './OrganosTable';
import type { TermoNode } from './taxonomiaTree';
import { TermoActionButtons, type TermoActionHandlers } from './TermoActionControls';

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
  /** Root-to-term chain of the open term; empty selects the worklist. */
  openPath: TermoNode[];
  unclassified: Organo[];
  termoActions: TermoActionHandlers;
}

export function TermoContentCard({ openPath, unclassified, termoActions }: TermoContentCardProps) {
  const pane = openPath.length > 0 ? termoPane(openPath) : unclassifiedPane(unclassified);
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

      {/* Wraps rather than holding one line: at 360 px the three term actions
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
            tree and nothing to delete. Assign lands here alongside them. */}
        <Group gap="xs">{openPath.length > 0 && <TermoActionButtons {...termoActions} />}</Group>
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
