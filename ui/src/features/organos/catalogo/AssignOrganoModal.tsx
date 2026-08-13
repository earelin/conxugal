import { Button, Modal, Stack } from '@mantine/core';
import { useState } from 'react';

import { strings } from '../../../shared/lib/strings';
import { DialogContextBlock } from '../dialogs/DialogContextBlock';
import { TermoDialogFooter } from '../dialogs/TermoDialogFooter';
import { TermoRefusalAlert } from '../dialogs/TermoRefusalAlert';
import type { Organo } from '../organos';
import type { Refusal } from '../taxonomia/termoRefusal';
import { TermoTreePicker } from '../taxonomia/TermoTreePicker';
import {
  findTermoPath,
  type TaxonomiaView,
  type TermoNode,
  termoPathLabel,
} from '../taxonomiaTree';
import { usePlaceOrgano } from './organoMutations';
import { organoPlacementLabel } from './organoPlacement';
import { placementRefusal } from './organoRefusal';
import { OrganoSelect } from './OrganoSelect';

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

/** Whether a chosen id is still in the section's current view of the world. */
function resolves(view: TaxonomiaView, target: AssignTarget, id: string): boolean {
  return target.kind === 'organo'
    ? findTermoPath(view.roots, id).length > 0
    : view.catalogue.some((organo) => organo.id === id);
}

function AssignOrganoForm({
  view,
  target,
  onAssigned,
  onCancel,
  onRefresh,
}: AssignOrganoFormProps) {
  const [pickedId, setPickedId] = useState<string | null>(null);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const placeOrgano = usePlaceOrgano();

  // Read back through the live view rather than trusted as held: both refusals
  // mean another administrator deleted a record, and the refresh they offer is
  // exactly when the choice already made can stop existing. Deriving it here
  // empties the picker and disables the primary in one move — otherwise the
  // dialog would show nothing selected and still let the same doomed pair be
  // submitted again.
  const choiceId = pickedId !== null && resolves(view, target, pickedId) ? pickedId : null;

  const organoId = target.kind === 'organo' ? target.organo.id : choiceId;
  const termoId = target.kind === 'termo' ? target.termo.id : choiceId;

  function choose(id: string) {
    setPickedId(id);
    // A refusal is about the pair that was submitted; leaving it up over a new
    // choice would assert a clash nothing has tested for.
    setRefusal(null);
  }

  function refresh() {
    // The message asked for a re-read; leaving it up afterwards would keep
    // asking for one that has already happened.
    setRefusal(null);
    onRefresh();
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
      <TermoRefusalAlert refusal={refusal} onRefresh={refresh} />

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
  const { target } = formProps;
  const title = target.kind === 'organo' ? copy.titleTermo : copy.titleOrgano;
  // The half already settled keys the form, so the pending choice can never
  // outlive the pair it belongs to and be read as the other half's id. Today
  // the dialog is always unmounted between two openings, which makes this
  // belt-and-braces — but the union exists to make that pairing unbreakable,
  // and without the key it holds only by the layout's good manners.
  const targetId = target.kind === 'organo' ? target.organo.id : target.termo.id;
  return (
    <Modal opened={opened} onClose={onCancel} title={title} radius="md">
      <AssignOrganoForm key={targetId} onCancel={onCancel} {...formProps} />
    </Modal>
  );
}
