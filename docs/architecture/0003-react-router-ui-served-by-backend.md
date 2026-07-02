---
status: accepted
date: 2026-06-29
spec: null
supersedes: null
superseded_by: null
---

# 0003. React Router UI served by the backend

## Status
Accepted

## Context
The system needs a web UI to browse, analyse and export contract data exposed by the
REST API ([ADR-0001](0001-backend-stack.md)). We must choose the UI technology and how it is delivered to
users. Options range from a separately deployed front-end (its own host/Node server)
to bundling the UI with the backend so there is a single deployable artifact.

## Decision
- Build the UI as a **React Router** application.
- **Serve the UI from the server application** — the backend exposes the built UI
  assets, so the Micronaut server is the single deployable unit and the single origin
  for both the UI and the REST API.

## Consequences
+ One deployable artifact and one origin: no separate front-end host and no
  cross-origin (CORS) configuration between UI and API.
+ Simpler operations — versioning, releasing and deploying UI and API together.
+ React Router gives standard client-side routing and data patterns for the UI.
− Couples UI and backend release cycles; shipping a UI-only change still rebuilds/
  redeploys the server artifact.
− The backend build must incorporate the UI build output; the build pipeline spans
  two toolchains (JVM + Node) and must orchestrate both.
