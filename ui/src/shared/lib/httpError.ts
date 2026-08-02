import { HttpError, ProblemError } from './httpClient';

export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof HttpError && error.status === status;
}

/**
 * Matches a refusal by its problem type rather than its status: several
 * refusals share a status (a move onto a descendant and a duplicate sibling name
 * are both 409), so only the type distinguishes the message to show.
 */
export function isProblemType(error: unknown, type: string): boolean {
  return error instanceof ProblemError && error.type === type;
}
