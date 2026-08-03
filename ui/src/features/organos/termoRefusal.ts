import { isProblemType } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import type { TermoNode } from './taxonomiaTree';

const PROBLEM_TYPE = {
  cycle: 'urn:conxugal:problem-type:termo-cycle',
  hasChildren: 'urn:conxugal:problem-type:termo-has-children',
  duplicateSiblingName: 'urn:conxugal:problem-type:duplicate-sibling-name',
  notFound: 'urn:conxugal:problem-type:termo-not-found',
} as const;

const copy = strings.admin.organos.termo;

export interface Refusal {
  /** An alert title where the refusal has one; null renders the message alone. */
  title: string | null;
  message: string;
}

/**
 * The refusals any term write can hit, keyed on the problem `type` and never on
 * the status — a cycle and a blocked-by-children delete are both 409, and a
 * shared "conflito" would leave the administrator nothing to act on. The
 * server's own `detail` is English and documented as freely rewordable, so it is
 * never parsed nor shown.
 *
 * Anything else — a transport failure, a 500, a 403 — falls through to the
 * generic message rather than being dressed up as a rule we can explain.
 */
function commonRefusal(error: unknown): Refusal {
  if (isProblemType(error, PROBLEM_TYPE.duplicateSiblingName)) {
    return { title: null, message: copy.duplicateSiblingName };
  }
  if (isProblemType(error, PROBLEM_TYPE.notFound)) {
    return { title: null, message: copy.notFound };
  }
  return { title: null, message: copy.genericError };
}

/**
 * Told apart from a move under a descendant because the mockup's wording —
 * "«X» é un termo fillo de «Y»" — is simply false when the target is the term
 * itself, and that is the case an administrator hits first.
 */
export function cycleRefusal(termo: TermoNode, target: TermoNode | null): Refusal {
  return {
    title: copy.cycleTitle,
    message:
      target === null || target.id === termo.id
        ? copy.cycleUnderSelf(termo.name)
        : copy.cycleUnderChild(target.name, termo.name),
  };
}

/** Names the children from the tree the section already holds; the problem body carries only ids. */
export function hasChildrenRefusal(children: TermoNode[]): Refusal {
  const names = children.map((child) => child.name);
  return {
    title: copy.hasChildrenTitle,
    message:
      names.length === 1
        ? copy.hasChildrenOne(names[0])
        : copy.hasChildrenOther(names.length, names.join('», «')),
  };
}

export function createRefusal(error: unknown): Refusal {
  return commonRefusal(error);
}

export function renameRefusal(error: unknown): Refusal {
  return commonRefusal(error);
}

export function moveRefusal(error: unknown, termo: TermoNode, target: TermoNode | null): Refusal {
  return isProblemType(error, PROBLEM_TYPE.cycle)
    ? cycleRefusal(termo, target)
    : commonRefusal(error);
}

export function deleteRefusal(error: unknown, termo: TermoNode): Refusal {
  return isProblemType(error, PROBLEM_TYPE.hasChildren)
    ? hasChildrenRefusal(termo.children)
    : commonRefusal(error);
}

/**
 * A duplicate name is the one refusal with a field to attach to, so the create
 * and rename dialogs report it on the input the administrator must edit rather
 * than as a banner above an apparently valid form.
 */
export function isDuplicateSiblingName(error: unknown): boolean {
  return isProblemType(error, PROBLEM_TYPE.duplicateSiblingName);
}
