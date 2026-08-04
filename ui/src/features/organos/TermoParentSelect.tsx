import { Select } from '@mantine/core';
import { useMemo } from 'react';

import { PATH_SEPARATOR, type TermoNode } from './taxonomiaTree';

// Mantine's Select carries string values, and the root is not a term with an id.
// Term ids are UUIDs, so this sentinel cannot collide with one.
const ROOT_VALUE = 'root';

interface TermoOption {
  value: string;
  label: string;
}

/**
 * Every term of the taxonomía as one flat option list, each labelled with its
 * full path. Nothing is filtered: a target that would be refused stays pickable
 * so the refusal can explain itself, which is the whole point of the cycle
 * alert — a destination missing from the list leaves the administrator guessing.
 *
 * The path is the label because term names repeat across branches; two
 * "Servizos centrais" options would otherwise be indistinguishable.
 */
function flattenTermoOptions(roots: TermoNode[], prefix = ''): TermoOption[] {
  return roots.flatMap((node) => {
    const label = prefix + node.name;
    return [
      { value: node.id, label },
      ...flattenTermoOptions(node.children, label + PATH_SEPARATOR),
    ];
  });
}

interface TermoParentSelectProps {
  roots: TermoNode[];
  label: string;
  /** The chosen term, or null for the root. */
  value: string | null;
  onChange: (termoId: string | null) => void;
  /**
   * Offers the root as a named destination rather than as an empty value. A move
   * to the root is a place to put a term, not the absence of one; a create reads
   * better with an empty field and a hint.
   */
  rootOptionLabel?: string;
  placeholder?: string;
  description?: string;
  required?: boolean;
}

export function TermoParentSelect({
  roots,
  label,
  value,
  onChange,
  rootOptionLabel,
  placeholder,
  description,
  required,
}: TermoParentSelectProps) {
  // The root is either an option of its own or the empty value, and that one
  // choice settles how the field clears and what an unset value means.
  const withRootOption = rootOptionLabel !== undefined;
  const data = useMemo(
    () =>
      rootOptionLabel === undefined
        ? flattenTermoOptions(roots)
        : [{ value: ROOT_VALUE, label: rootOptionLabel }, ...flattenTermoOptions(roots)],
    [roots, rootOptionLabel],
  );

  return (
    <Select
      label={label}
      description={description}
      placeholder={placeholder}
      required={required}
      data={data}
      value={value ?? (withRootOption ? ROOT_VALUE : null)}
      clearable={!withRootOption}
      allowDeselect={!withRootOption}
      onChange={(selected) =>
        onChange(selected === null || selected === ROOT_VALUE ? null : selected)
      }
    />
  );
}
