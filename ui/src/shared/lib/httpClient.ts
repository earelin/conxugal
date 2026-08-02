export class HttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
  }
}

/**
 * A refusal the server described with an RFC 9457 `application/problem+json`
 * body. It extends `HttpError` rather than standing beside it because the single
 * QueryClient keys both its retry policy and its 401 redirect on
 * `error instanceof HttpError` — a separate error type would silently opt every
 * problem+json refusal out of both.
 *
 * Callers key messages on `type`, never on `status`: several distinct refusals
 * share a status, and only the type tells them apart.
 */
export class ProblemError extends HttpError {
  readonly type: string;
  readonly detail: string | null;

  constructor(status: number, type: string, detail: string | null, message: string) {
    super(status, message);
    this.name = 'ProblemError';
    this.type = type;
    this.detail = detail;
  }
}

interface ProblemBody {
  type: string;
  title?: unknown;
  detail?: unknown;
}

function isProblemBody(body: unknown): body is ProblemBody {
  return (
    typeof body === 'object' && body !== null && typeof (body as ProblemBody).type === 'string'
  );
}

async function errorFor(response: Response): Promise<HttpError> {
  const fallbackMessage = `Request failed with status ${response.status}`;
  if (!response.headers.get('Content-Type')?.includes('application/problem+json')) {
    return new HttpError(response.status, fallbackMessage);
  }

  // A body that never arrives or does not parse must not mask the HTTP failure
  // itself, so a broken problem+json degrades to the plain error.
  const body: unknown = await response.json().catch(() => null);
  if (!isProblemBody(body)) {
    return new HttpError(response.status, fallbackMessage);
  }

  const detail = typeof body.detail === 'string' ? body.detail : null;
  const title = typeof body.title === 'string' ? body.title : null;
  return new ProblemError(response.status, body.type, detail, detail ?? title ?? fallbackMessage);
}

export async function apiFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  const response = await fetch(input, { ...init, headers });
  if (!response.ok) {
    throw await errorFor(response);
  }
  return response;
}
