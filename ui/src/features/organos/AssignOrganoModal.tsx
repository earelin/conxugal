import { Button, Modal, Stack } from '@mantine/core';
import { useState } from 'react';

import { strings } from '../../shared/lib/strings';
import { DialogContextBlock } from './DialogContextBlock';
import { usePlaceOrgano } from './organoMutations';
import { organoPlacementLabel } from './organoPlacement';
import { placementRefusal } from './organoRefusal';
import type { Organo } from './organos';
import { OrganoSelect } from './OrganoSelect';
import { findTermoPath, type TaxonomiaView, type TermoNode, termoPathLabel } from './taxonomiaTree';
import { TermoDialogFooter } from './TermoDialogFooter';
import type { Refusal } from './termoRefusal';
import { TermoRefusalAlert } from './TermoRefusalAlert';
import { TermoTreePicker } from './TermoTreePicker';

const copy = strings.admin.organos.assign;

/**
 * Which half of the pair the entry point already settled. A union rather than
 * two optional props, so neither "both" nor "neither" is expressible: from a
 * worklist row the Órgano is known and the term is the question, and from a
 * term's header it is the other way round.
 */
export type AssignTarget = { kind: 'organo'; organo: Organo } | { kind: 'termo'; termo: TermoNode };

interface AssignOrganoFormProps {
  view: TaxonomiaView;
  target: AssignTarget;
  onAssigned: () => void;
  onCancel: () => void;
  /** Re-reads the section, which is the only way past either refusal. */
  onRefresh: () => void;
}

function AssignOrganoForm({
  view,
  target,
  onAssigned,
  onCancel,
  onRefresh,
}: AssignOrganoFormProps) {
  // A reassignment opens on the Órgano's current term, the way the move dialog
  // opens on a term's current parent.
  const [choiceId, setChoiceId] = useState<string | null>(
    target.kind === 'organo' ? target.organo.termoId : null,
  );
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const placeOrgano = usePlaceOrgano();

  const organoId = target.kind === 'organo' ? target.organo.id : choiceId;
  const termoId = target.kind === 'termo' ? target.termo.id : choiceId;

  function choose(id: string) {
    setChoiceId(id);
    // A refusal is about the pair that was submitted; leaving it up over a new
    // choice would assert a clash nothing has tested for.
    setRefusal(null);
  }

  function onSubmit() {
    if (organoId === null || termoId === null) {
      return;
    }
    setRefusal(null);
    placeOrgano.mutate(
      { organoId, termoId },
      { onSuccess: onAssigned, onError: (error) => setRefusal(placementRefusal(error)) },
    );
  }

  return (
    <Stack gap="md">
      <TermoRefusalAlert refusal={refusal} onRefresh={onRefresh} />

      {target.kind === 'organo' ? (
        <>
          <DialogContextBlock
            label={copy.organoLabel}
            name={target.organo.name}
            note={copy.currently(organoPlacementLabel(view.roots, target.organo))}
          />
          <TermoTreePicker
            roots={view.roots}
            label={copy.termoLabel}
            required
            value={choiceId}
            onChange={choose}
          />
        </>
      ) : (
        <>
          <DialogContextBlock
            label={copy.termoLabel}
            name={termoPathLabel(findTermoPath(view.roots, target.termo.id))}
          />
          <OrganoSelect
            organos={view.catalogue}
            roots={view.roots}
            label={copy.organoLabel}
            required
            value={choiceId}
            onChange={choose}
          />
        </>
      )}

      <TermoDialogFooter onCancel={onCancel}>
        <Button onClick={onSubmit} disabled={choiceId === null} loading={placeOrgano.isPending}>
          {copy.submit}
        </Button>
      </TermoDialogFooter>
    </Stack>
  );
}

interface AssignOrganoModalProps extends AssignOrganoFormProps {
  opened: boolean;
}

export function AssignOrganoModal({ opened, onCancel, ...formProps }: AssignOrganoModalProps) {
  const title = formProps.target.kind === 'organo' ? copy.titleTermo : copy.titleOrgano;
  return (
    <Modal opened={opened} onClose={onCancel} title={title} radius="md">
      <AssignOrganoForm onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
