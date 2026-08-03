import { isProblemType } from '../../shared/lib/httpError';
import { strings } from '../../shared/lib/strings';
import { findTermoPath, type TermoNode } from './taxonomiaTree';

const PROBLEM_TYPE = {
  cycle: 'urn:conxugal:problem-type:termo-cycle',
  hasChildren: 'urn:conxugal:problem-type:termo-has-children',
  duplicateSiblingName: 'urn:conxugal:problem-type:duplicate-sibling-name',
  notFound: 'urn:conxugal:problem-type:termo-not-found',
} as const;

const copy = strings.admin.organos.termo;

export interface Refusal {
  /** An alert title where the refusal has one; otherwise the message stands alone. */
  title?: string;
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
export function termoRefusal(error: unknown): Refusal {
  if (isProblemType(error, PROBLEM_TYPE.duplicateSiblingName)) {
    return { message: copy.duplicateSiblingName };
  }
  if (isProblemType(error, PROBLEM_TYPE.notFound)) {
    return { message: copy.notFound };
  }
  return { message: copy.genericError };
}

/**
 * The rule the move dialog refuses on, stated once: a term cannot land on
 * itself, nor anywhere inside its own branch, because either detaches that
 * branch from the tree. The server checks it too and is the authority; this is
 * what lets the dialog explain a destination it can already see is impossible.
 */
export function wouldCycle(termo: TermoNode, target: TermoNode | null): boolean {
  if (target === null) {
    return false;
  }
  return target.id === termo.id || findTermoPath(termo.children, target.id).length > 0;
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

/**
 * Names the children from the tree the section already holds. An empty list is
 * not "no children" — it is the server refusing over children this browser has
 * not read yet, which is the only way this refusal reaches the wire at all,
 * since a term with visible children never gets a delete request sent.
 */
export function hasChildrenRefusal(children: TermoNode[]): Refusal {
  const names = children.map((child) => child.name);
  if (names.length === 0) {
    return { title: copy.hasChildrenTitle, message: copy.hasChildrenUnknown };
  }
  return {
    title: copy.hasChildrenTitle,
    message:
      names.length === 1
        ? copy.hasChildrenOne(names[0])
        : copy.hasChildrenOther(names.length, names.join('», «')),
  };
}

export function moveRefusal(error: unknown, termo: TermoNode, target: TermoNode | null): Refusal {
  return isProblemType(error, PROBLEM_TYPE.cycle)
    ? cycleRefusal(termo, target)
    : termoRefusal(error);
}

export function deleteRefusal(error: unknown, termo: TermoNode): Refusal {
  return isProblemType(error, PROBLEM_TYPE.hasChildren)
    ? hasChildrenRefusal(termo.children)
    : termoRefusal(error);
}

/**
 * A duplicate name is the one refusal with a field to attach to, so the create
 * and rename dialogs report it on the input the administrator must edit rather
 * than as a banner above an apparently valid form.
 */
export function isDuplicateSiblingName(error: unknown): boolean {
  return isProblemType(error, PROBLEM_TYPE.duplicateSiblingName);
}
