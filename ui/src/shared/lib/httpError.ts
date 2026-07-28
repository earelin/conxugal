import { HttpError } from './httpClient';

export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof HttpError && error.status === status;
}
