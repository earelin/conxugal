# Documentation conventions

The `docs/` tree follows the `SPEC → feature → task` flow, with ADRs recorded
orthogonally. Per-doc-type authoring — format, frontmatter, and rules — is owned
by the skills (`create-spec`, `create-feature`, `create-task`, `create-adr`); the
per-folder `CLAUDE.md` files only record repo-specific overrides. This file holds
the conventions that apply across every doc.

## Diagrams

- **Use Mermaid for diagrams.** Any diagram — flow, sequence, component,
  dependency, state, ER — is a fenced ```mermaid block, never ASCII art.
- **Text diagrams are only for folder/file trees.** A directory layout may be
  drawn as an indented text tree in a fenced code block; everything else is Mermaid.
