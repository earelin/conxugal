---
status: accepted
date: 2026-06-29
spec: null
supersedes: null
superseded_by: null
---

# 0001. Backend stack: Micronaut, Java 25, PostgreSQL, REST API

## Status
Accepted

## Context
The project needs a server to extract, store, analyse and export public-contract
information from contratosdegalicia.gal. We must pick a runtime, language, framework,
persistence engine and the style of the public contract the server exposes.

The workload is I/O-bound (scraping/ingesting external data, serving queries and
exports) with a relational shape — contracts, awarding bodies, tenders and their
relationships — that benefits from a mature SQL engine. We want fast startup, low
memory footprint, ahead-of-time compilation friendliness, and first-class testing.

## Decision
- **Language:** Java 25 (LTS).
- **Framework:** Micronaut for the server application.
- **Persistence:** PostgreSQL as the relational datastore.
- **Public contract:** a REST API.

## Consequences
+ Micronaut's compile-time DI/AOP gives fast startup and low memory, and a smooth
  path to GraalVM native images if needed later.
+ PostgreSQL fits the relational domain and offers strong querying, JSON columns
  for semi-structured scraped payloads, and full-text search for analysis/export.
+ Java 25 LTS provides a long support window and modern language features.
+ REST is well understood, easy to consume from the UI and from external clients,
  and simple to document (OpenAPI).
− Commits the team to the JVM/Micronaut ecosystem and its conventions.
− REST (vs. GraphQL/RPC) may require extra endpoints for some aggregate/export
  queries; revisit with a new ADR if query flexibility becomes a constraint.
