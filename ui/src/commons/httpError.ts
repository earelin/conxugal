import { HttpError } from '../api/httpClient';

export function isHttpStatus(error: unknown, status: number): boolean {
  return error instanceof HttpError && error.status === status;
}
