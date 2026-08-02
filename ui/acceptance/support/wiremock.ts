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
  await clearRequestJournal();
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

/**
 * Serves `samples` as a Server-Sent Events stream, one sample per chunk.
 *
 * WireMock cannot hold a connection open, so a stream is a finite body dribbled
 * out in `numberOfChunks` equal *byte* slices. Equal-sized slices only coincide
 * with event frames if the frames themselves are the same length, so each one is
 * padded to a common width with an SSE comment line (ignored by `EventSource`).
 * Without that padding a chunk boundary lands mid-frame and a sample surfaces at
 * an arrival time no test can predict.
 *
 * The leading `retry:` field pins how long the browser waits before reconnecting
 * once the body ends, which is what makes the reconnecting state observable for a
 * chosen window instead of Chrome's default.
 *
 * `heartbeats` appends that many comment-only chunks after the last sample — the
 * same thing the real endpoint sends to hold a quiet connection open. Without
 * them the body ends the instant the last sample lands and the panel spends only
 * a moment in its live state, too briefly to assert against. Pass `0` when the
 * drop itself is what a scenario is after.
 */
export async function stubEventStream(
  urlPath: string,
  samples: unknown[],
  {
    spacingMillis,
    retryMillis,
    heartbeats = 0,
  }: { spacingMillis: number; retryMillis: number; heartbeats?: number },
): Promise<void> {
  const frames = samples
    .map(
      (sample, index) =>
        (index === 0 ? `retry: ${retryMillis}\n\n` : '') + `data: ${JSON.stringify(sample)}\n\n`,
    )
    .concat(Array<string>(heartbeats).fill(''));
  // Two bytes is the shortest possible comment (':' and its newline), so the
  // widest frame still needs room for one — and an empty heartbeat chunk is
  // nothing but that comment.
  const width = Math.max(...frames.map((frame) => frame.length)) + 2;
  const padded = frames.map((frame) => `${frame}:${'.'.repeat(width - frame.length - 2)}\n`);

  await admin('/mappings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: { method: 'GET', urlPath },
      response: {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
        body: padded.join(''),
        chunkedDribbleDelay: {
          numberOfChunks: padded.length,
          totalDuration: spacingMillis * padded.length,
        },
      },
    }),
  });
}

/** Forgets the requests recorded so far, leaving the programmed stubs in place. */
export async function clearRequestJournal(): Promise<void> {
  await admin('/requests', { method: 'DELETE' });
}

/**
 * How many times the SPA called an endpoint. Separate from `bodiesSentTo`, which
 * parses each body as JSON and so cannot count bodyless `GET`s.
 */
export async function requestCountFor(method: string, urlPath: string): Promise<number> {
  const response = await admin('/requests', { method: 'GET' });
  const recorded = (await response.json()) as {
    requests: { request: { method: string; url: string } }[];
  };
  return recorded.requests.filter(
    (entry) => entry.request.method === method && entry.request.url === urlPath,
  ).length;
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
