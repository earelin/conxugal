import {
  type ComboboxItem,
  Group,
  type OptionsFilter,
  Select,
  type SelectProps,
  Text,
} from '@mantine/core';
import { useMemo } from 'react';

import { strings } from '../../shared/lib/strings';
import { organoPlacementLabel } from './organoPlacement';
import type { Organo } from './organos';
import type { TermoNode } from './taxonomiaTree';
import { foldForSearch } from './termoSearch';

const copy = strings.admin.organos.assign;

// Dropdown rows have no space for the table's state Badge, and opacity alone
// says nothing to a screen reader, so an inactive Órgano says so in its text as
// well as reading dimmer.
const INACTIVE_OPACITY = 0.6;

interface OrganoOption {
  organo: Organo;
  /** Where it sits now, prefixed with its state when it is inactive. */
  placement: string;
}

function buildOptions(organos: Organo[], roots: TermoNode[]): Map<string, OrganoOption> {
  return new Map(
    organos.map((organo) => {
      const placement = organoPlacementLabel(roots, organo);
      return [
        organo.id,
        { organo, placement: organo.active ? placement : `${copy.inactive} · ${placement}` },
      ];
    }),
  );
}

/**
 * Mantine's own filter compares the raw label, which is accent-sensitive: nobody
 * typing «saude» would find «Servizo Galego de Saúde». It matches the name only
 * — an option's placement is context, and letting it match would bury the Órgano
 * being typed under every one filed in the same branch.
 */
const filterByName: OptionsFilter = ({ options, search, limit }) => {
  const query = foldForSearch(search.trim());
  return (options as ComboboxItem[])
    .filter((option) => foldForSearch(option.label).includes(query))
    .slice(0, limit);
};

export interface OrganoSelectProps {
  /** The whole catalogue, in the server's name order. */
  organos: Organo[];
  /** The tree, so each option can say where its Órgano currently sits. */
  roots: TermoNode[];
  label: string;
  value: string | null;
  onChange: (organoId: string) => void;
  required?: boolean;
}

/**
 * Picks one Órgano out of the catalogue, each option stating where it is filed
 * now — which is what makes a reassignment legible before it is confirmed.
 *
 * The option's *label* stays the bare name: Mantine writes it into the search
 * box once picked, and a name with its whole term path appended would leave the
 * closed field unreadable. The placement is rendered from a lookup by id, the
 * same way the tree card reads a term's count.
 */
export function OrganoSelect({
  organos,
  roots,
  label,
  value,
  onChange,
  required,
}: OrganoSelectProps) {
  const optionsById = useMemo(() => buildOptions(organos, roots), [organos, roots]);
  const data = useMemo(
    () => organos.map((organo) => ({ value: organo.id, label: organo.name })),
    [organos],
  );

  const renderOption: SelectProps['renderOption'] = ({ option }) => {
    const entry = optionsById.get(option.value);
    return (
      <Group gap="xs" wrap="nowrap" opacity={entry?.organo.active === false ? INACTIVE_OPACITY : 1}>
        <Text size="sm">{option.label}</Text>
        <Text size="sm" c="dimmed">
          ({entry?.placement})
        </Text>
      </Group>
    );
  };

  return (
    <Select
      label={label}
      required={required}
      placeholder={copy.organoPlaceholder}
      data={data}
      value={value}
      searchable
      filter={filterByName}
      renderOption={renderOption}
      nothingFoundMessage={copy.noOrganoMatches}
      // The field is required and replaces a placement; clearing it would leave
      // the dialog with nothing to submit rather than expressing anything.
      clearable={false}
      allowDeselect={false}
      onChange={(selected) => {
        if (selected !== null) {
          onChange(selected);
        }
      }}
    />
  );
}
