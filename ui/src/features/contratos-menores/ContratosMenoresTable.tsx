import { Anchor, Group, Stack, Table, Text } from '@mantine/core';
import { IconExternalLink, IconInfoCircle } from '@tabler/icons-react';
import type { CSSProperties, ReactNode } from 'react';

import { strings } from '../../shared/lib/strings';
import { formatAmount, formatPublicationDate } from './contractFormat';
import type { ContratoMenor } from './contracts';

const copy = strings.contratosMenores;

// The admin tables' column-header treatment, which this one adopts unchanged.
const COLUMN_HEADER = { tt: 'uppercase', fz: 'xs', c: 'dimmed' } as const;

// Everything except the object takes only the width its content needs, so the
// object column absorbs the wrapping instead of them. It is the only value with
// no length the source respects, and the one a reader actually reads.
const NARROW_COLUMN: CSSProperties = { whiteSpace: 'nowrap', width: '1%' };

// The identifiers under the date and the awardee are copied rather than read,
// which is what the monospace is for; dimmed because neither is the value the
// column is named after.
const IDENTIFIER = { size: 'xs', c: 'dimmed', ff: 'monospace' } as const;

/**
 * The two values a row can lack. Nothing else has one of these: a contract
 * without its publication date, its amount or its awardee never reaches a
 * reader, so there is no absent form of those three to write.
 */
function OrNotPublished({ children }: { children: string | null }): ReactNode {
  if (children === null) {
    return (
      <Text fs="italic" c="dimmed">
        {copy.notPublished}
      </Text>
    );
  }
  return <Text size="sm">{children}</Text>;
}

function ContractRow({ contract }: { contract: ContratoMenor }): ReactNode {
  const sourceId = String(contract.sourceId);
  return (
    <Table.Tr>
      <Table.Td style={NARROW_COLUMN}>
        <Stack gap={0}>
          <Text size="sm" fw={500}>
            {formatPublicationDate(contract.publicationDate)}
          </Text>
          <Text {...IDENTIFIER}>{sourceId}</Text>
        </Stack>
      </Table.Td>
      <Table.Td>
        <OrNotPublished>{contract.obxecto}</OrNotPublished>
      </Table.Td>
      {/* Text, deliberately: the operador route belongs to another spec's read
          feature, and a link to a route that 404s is worse than none. That
          feature adds the crossing here. */}
      <Table.Td style={NARROW_COLUMN}>
        <Stack gap={0}>
          <Text size="sm">{contract.awardee.name}</Text>
          <Text {...IDENTIFIER}>{contract.awardee.fiscalId}</Text>
        </Stack>
      </Table.Td>
      <Table.Td ta="right" fw={600} style={NARROW_COLUMN}>
        {formatAmount(contract.amount)}
      </Table.Td>
      <Table.Td style={NARROW_COLUMN}>
        <OrNotPublished>{contract.duration}</OrNotPublished>
      </Table.Td>
      <Table.Td ta="right" style={NARROW_COLUMN}>
        <Anchor
          href={contract.sourceUrl}
          target="_blank"
          rel="noreferrer"
          // The column repeats down the page and «Fonte» names no contract, so
          // the only thing that tells one of these links from the next is the
          // contract it says it opens.
          aria-label={copy.sourceLinkLabel(sourceId)}
          display="inline-flex"
        >
          <IconExternalLink size={18} aria-hidden />
        </Anchor>
      </Table.Td>
    </Table.Tr>
  );
}

/**
 * Every attribute the system holds for a contrato menor, on one row: the family
 * has no detail view to click into, which is why the row is wide.
 *
 * The object, the awardee's name and the duration are rendered exactly as they
 * were returned — nothing truncates, case-folds or reformats them. Only the date
 * and the amount are written in a spelling of this list's own, which is
 * presentation rather than a correction of what the source published.
 */
export function ContratosMenoresTable({ contracts }: { contracts: ContratoMenor[] }) {
  return (
    // Six columns need more than a 360 px card can give, so the table scrolls
    // inside its own region rather than dragging the whole page sideways.
    <Table.ScrollContainer minWidth={720} type="native">
      <Table aria-label={copy.tableLabel} verticalSpacing="sm">
        <Table.Thead>
          <Table.Tr>
            <Table.Th {...COLUMN_HEADER}>{copy.columnDate}</Table.Th>
            <Table.Th {...COLUMN_HEADER}>{copy.columnObxecto}</Table.Th>
            <Table.Th {...COLUMN_HEADER}>{copy.columnAwardee}</Table.Th>
            {/* Two lines, and the second is not decoration: an amount whose VAT
                is unstated invites comparison against thresholds that exclude
                it. */}
            <Table.Th {...COLUMN_HEADER} ta="right" style={NARROW_COLUMN}>
              <Stack gap={0}>
                <span>{copy.columnAmount}</span>
                <span>{copy.columnAmountVat}</span>
              </Stack>
            </Table.Th>
            <Table.Th {...COLUMN_HEADER} style={NARROW_COLUMN}>
              <Group gap={4} wrap="nowrap">
                {copy.columnDuration}
                {/* The mark is on the column, and the caption below spells out
                    what it means: one statement covers every row and reads
                    once. Hidden from the accessibility tree because that caption
                    is already part of the table a screen reader announces. */}
                <IconInfoCircle size={14} aria-hidden />
              </Group>
            </Table.Th>
            <Table.Th {...COLUMN_HEADER} ta="right" style={NARROW_COLUMN}>
              {copy.columnSource}
            </Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {contracts.map((contract) => (
            <ContractRow key={contract.sourceId} contract={contract} />
          ))}
        </Table.Tbody>
        {/* A real `<caption>`, so the caveat belongs to the table rather than
            merely sitting under it. */}
        <Table.Caption>
          <Group gap={6} justify="flex-start" wrap="nowrap">
            <IconInfoCircle size={14} aria-hidden />
            <Text size="xs" c="dimmed" ta="left">
              {copy.durationCaveat}
            </Text>
          </Group>
        </Table.Caption>
      </Table>
    </Table.ScrollContainer>
  );
}
