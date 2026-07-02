---
feat: FEAT-0003
adrs: [0003, 0004]
status: todo
depends_on: []
---

# Wire the UI build into the server artifact

Governed by [ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md)
(single deployable artifact). Build-pipeline plumbing only — no application code.

## Scope
- A Gradle step (in `application/build.gradle` or a shared convention) that produces
  `ui/dist` (invoking the UI's `npm run build`) and copies its contents into a location
  Micronaut serves statically from the packaged artifact (e.g. a resources directory
  consumed by task TASK-0002's static-resource config).
- Wire this step into `application`'s `build`/`assemble`/`run` so the two toolchains
  (JVM + Node, per ADR-0003) are orchestrated by one command.
- `.gitignore` the generated/copied output; `ui/dist` and its copy are build artifacts,
  not source.

## Acceptance criteria
- Running `./gradlew build` from `server/` (with no prior manual `npm run build`)
  produces an artifact containing the current UI build output.
- Running `./gradlew run` from `server/` serves that build without requiring a separate
  `npm run dev`/`npm run build` step by hand.
- A UI-only source change is picked up by the next `./gradlew build` without a stale
  cache issue (rebuilding `ui/dist` isn't skipped when UI sources changed).
