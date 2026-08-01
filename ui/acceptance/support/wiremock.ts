/**
 * Client for the WireMock admin API, used to put the stubbed backend into a
 * known state per scenario and to inspect what the SPA actually sent.
 *
 * The base URL comes from the environment so the same specs run against local
 * docker-compose and any other host without edits.
 */
const ADMIN = `${process.env.WIREMOCK_URL ?? 'http://localhost:8081'}/__admin`;

async function admin(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(`${ADMIN}${path}`, init);
  if (!response.ok) {
    throw new Error(`WireMock admin ${path} failed with ${response.status}`);
  }
  return response;
}

/** Drops stubs added during a scenario, restoring the mappings on disk. */
export async function resetMappings(): Promise<void> {
  await admin('/mappings/reset', { method: 'POST' });
  await admin('/requests', { method: 'DELETE' });
}

/**
 * Serves `body` as JSON for this scenario. Later-added mappings win over the
 * ones loaded from disk, so this overrides a default without editing it.
 */
export async function stubJson(method: string, urlPath: string, body: unknown): Promise<void> {
  await admin('/mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: { method, urlPath },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        jsonBody: body,
      },
    }),
  });
}

/** The JSON bodies the SPA sent to an endpoint, oldest first. */
export async function bodiesSentTo(method: string, urlPath: string): Promise<unknown[]> {
  const response = await admin('/requests', { method: 'GET' });
  const recorded = (await response.json()) as {
    requests: { request: { method: string; url: string; body: string } }[];
  };
  return recorded.requests
    .map((entry) => entry.request)
    .filter((request) => request.method === method && request.url === urlPath)
    .reverse()
    .map((request) => JSON.parse(request.body) as unknown);
}
