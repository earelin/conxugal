# Documentation conventions

General rules for everything under `docs/`. The per-folder `CLAUDE.md` files
(`specs/`, `features/`, `architecture/`) add rules specific to each doc type.

## Diagrams

- **Use Mermaid for diagrams.** Any diagram — flow, sequence, component,
  dependency, state, ER — is a fenced ```mermaid block, never ASCII art.
- **Text diagrams are only for folder/file trees.** A directory layout may be
  drawn as an indented text tree in a fenced code block; everything else is Mermaid.
