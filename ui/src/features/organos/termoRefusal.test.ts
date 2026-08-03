import { describe, expect, it } from 'vitest';

import { HttpError, ProblemError } from '../../shared/lib/httpClient';
import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';
import {
  cycleRefusal,
  deleteRefusal,
  hasChildrenRefusal,
  isDuplicateSiblingName,
  moveRefusal,
  termoRefusal,
  wouldCycle,
} from './termoRefusal';

const copy = strings.admin.organos.termo;

function termo(id: string, name: string, children: TermoNode[] = []): TermoNode {
  return { id, name, children, organos: [] };
}

function problem(status: number, type: string): ProblemError {
  return new ProblemError(status, `urn:conxugal:problem-type:${type}`, null, type);
}

//        consellerias
//        ├── sanidade
//        │   └── innovacion
//        └── educacion
//        concellos (a second root)
const innovacion = termo('t-4', 'Axencia Galega de Innovación');
const sanidade = termo('t-2', 'Consellería de Sanidade', [innovacion]);
const educacion = termo('t-5', 'Consellería de Educación');
const consellerias = termo('t-1', 'Consellerías', [sanidade, educacion]);
const concellos = termo('t-3', 'Concellos');

describe('wouldCycle', () => {
  it('allows the root, which is a destination like any other', () => {
    expect(wouldCycle(sanidade, null)).toBe(false);
  });

  it('refuses a term onto itself', () => {
    expect(wouldCycle(sanidade, sanidade)).toBe(true);
  });

  it('refuses a term onto its own child', () => {
    expect(wouldCycle(sanidade, innovacion)).toBe(true);
  });

  it('refuses a term onto a deeper descendant, not just a direct child', () => {
    expect(wouldCycle(consellerias, innovacion)).toBe(true);
  });

  it('allows a move up to an ancestor, which detaches nothing', () => {
    expect(wouldCycle(innovacion, consellerias)).toBe(false);
  });

  it('allows a move sideways to a sibling and to another branch', () => {
    expect(wouldCycle(sanidade, educacion)).toBe(false);
    expect(wouldCycle(sanidade, concellos)).toBe(false);
  });
});

describe('cycleRefusal', () => {
  it('says a term cannot go inside itself, which is not a claim about children', () => {
    const { message } = cycleRefusal(sanidade, sanidade);

    expect(message).toBe(copy.cycleUnderSelf(sanidade.name));
    // The mockup's "«X» é un termo fillo de «Y»" is false here, and this is the
    // case an administrator hits first.
    expect(message).not.toBe(copy.cycleUnderChild(sanidade.name, sanidade.name));
  });

  it('names both terms when the target is a descendant', () => {
    const { title, message } = cycleRefusal(sanidade, innovacion);

    expect(title).toBe(copy.cycleTitle);
    expect(message).toBe(copy.cycleUnderChild(innovacion.name, sanidade.name));
  });
});

describe('hasChildrenRefusal', () => {
  it('names the single child', () => {
    const { title, message } = hasChildrenRefusal([innovacion]);

    expect(title).toBe(copy.hasChildrenTitle);
    expect(message).toBe(copy.hasChildrenOne(innovacion.name));
  });

  it('names every child when there is more than one', () => {
    const { message } = hasChildrenRefusal([sanidade, educacion]);

    expect(message).toBe(copy.hasChildrenOther(2, `${sanidade.name}», «${educacion.name}`));
    expect(message).toContain(sanidade.name);
    expect(message).toContain(educacion.name);
  });

  it('claims no count for children this browser has not read', () => {
    // The refusal only reaches the wire when the local tree shows no children,
    // and the problem body carries no ids, so there is nothing to name.
    const { title, message } = hasChildrenRefusal([]);

    expect(title).toBe(copy.hasChildrenTitle);
    expect(message).toBe(copy.hasChildrenUnknown);
    expect(message).not.toContain('0');
  });
});

describe('termoRefusal', () => {
  it('tells the two 409s apart by type, since the status cannot', () => {
    const duplicate = termoRefusal(problem(409, 'duplicate-sibling-name'));
    const cycle = moveRefusal(problem(409, 'termo-cycle'), sanidade, innovacion);

    expect(duplicate.message).toBe(copy.duplicateSiblingName);
    expect(cycle.message).toBe(copy.cycleUnderChild(innovacion.name, sanidade.name));
    expect(duplicate.message).not.toBe(cycle.message);
  });

  it('explains a term someone else removed', () => {
    expect(termoRefusal(problem(404, 'termo-not-found')).message).toBe(copy.notFound);
  });

  it.each([
    ['a problem type this UI does not model', problem(409, 'termo-locked')],
    ['a plain HTTP failure with no problem body', new HttpError(500, 'boom')],
    ['a transport failure', new TypeError('Failed to fetch')],
  ])('falls back to the generic message for %s', (_case, error) => {
    const { title, message } = termoRefusal(error);

    // Not dressed up as a rule we can explain — and not silently blank.
    expect(message).toBe(copy.genericError);
    expect(title).toBeUndefined();
  });
});

describe('moveRefusal and deleteRefusal', () => {
  it('routes a delete blocked by children to the children message', () => {
    expect(deleteRefusal(problem(409, 'termo-has-children'), sanidade).message).toBe(
      copy.hasChildrenOne(innovacion.name),
    );
  });

  it('routes any other delete failure through the shared refusals', () => {
    expect(deleteRefusal(problem(404, 'termo-not-found'), sanidade).message).toBe(copy.notFound);
    expect(deleteRefusal(new HttpError(500, 'boom'), sanidade).message).toBe(copy.genericError);
  });

  it('routes a move refused for a duplicate name to the name message, not the cycle one', () => {
    const { message } = moveRefusal(problem(409, 'duplicate-sibling-name'), sanidade, concellos);

    expect(message).toBe(copy.duplicateSiblingName);
    expect(message).not.toBe(copy.cycleUnderChild(concellos.name, sanidade.name));
  });
});

describe('isDuplicateSiblingName', () => {
  it('picks out the one refusal that has a field to land on', () => {
    expect(isDuplicateSiblingName(problem(409, 'duplicate-sibling-name'))).toBe(true);
    expect(isDuplicateSiblingName(problem(409, 'termo-cycle'))).toBe(false);
    expect(isDuplicateSiblingName(new HttpError(409, 'boom'))).toBe(false);
  });
});
