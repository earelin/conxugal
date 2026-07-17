# conxugal

Extrae, almacena, analiza e exporta a información de contratos públicos da Xunta de Galicia.

## Propósito xeral

**conxugal** fai accesible e analizable a información de contratación pública da Xunta de
Galicia, publicada en [contratosdegalicia.gal](https://www.contratosdegalicia.gal/). Para iso:

- **Importa** os contratos públicos da Xunta dende contratosdegalicia.gal, almacenándoos de
  forma estruturada para o seu uso posterior.
- **Mostra** os contratos nunha interface web que permite **buscar** e **analizar** a
  información de xeito sinxelo.

## Estrutura

- `server/` — backend en **Micronaut** (Java 25) con arquitectura hexagonal
  ([ADR-0002](docs/architecture/0002-hexagonal-architecture.md)): módulos `domain`,
  `application` e `infrastructure`, máis un módulo `acceptance` de probas de caixa negra.
  Encárgase da importación e almacenamento dos datos en **PostgreSQL**, a API de consulta
  e serve a UI como artefacto único (ver [`server/README.md`](server/README.md)).
- `ui/` — interface web de busca e análise: SPA en **Vite** + **React** + **React Router**
  + **[Mantine](https://mantine.dev)**. Compílase a activos estáticos que serve o backend
  (ver [`ui/README.md`](ui/README.md)).
- `docs/` — especificacións (`specs/`), funcionalidades (`features/`) e decisións de
  arquitectura (`architecture/`, ADRs), seguindo o fluxo de traballo
  *spec → feature → task*.

## Requisitos

- **Java 25** (o toolchain provisiónao Gradle automaticamente se non está instalado).
- **Node.js 20+** e **npm** (desenvolvido con Node 24).
- **PostgreSQL** como almacenamento
  ([ADR-0001](docs/architecture/0001-backend-stack.md)).

## Posta en marcha

Backend (dende `server/`):

```bash
./gradlew run     # arranca o servidor en http://localhost:8080
./gradlew build   # compila, executa as probas e ensambla os módulos
```

UI (dende `ui/`):

```bash
npm install
npm run dev       # servidor de desenvolvemento en http://localhost:5173
npm run build     # xera os activos estáticos en dist/
```

Máis detalles de cada compoñente en [`server/README.md`](server/README.md) e
[`ui/README.md`](ui/README.md).

## Linters de documentación e CI

Tres scripts verifican a documentación, os fluxos de traballo e o contrato OpenAPI antes
de facer commit (CI vólveos comprobar ao facer push):

- `scripts/docs-lint.sh` — formato Markdown, ligazóns internas e diagramas Mermaid de
  `docs/` e dos `*.md` da raíz.
- `scripts/actions-lint.sh` — os fluxos de GitHub Actions de `.github/workflows/`.
- `scripts/openapi-lint.sh` — o contrato REST de `docs/api/openapi.yaml`
  ([ADR-0010](docs/architecture/0010-design-first-openapi-contract.md)).

Requiren estas ferramentas (os scripts avisan se falta algunha):

```bash
# docs-lint.sh
npm install -g markdownlint-cli2 @probelabs/maid   # formato Markdown + Mermaid
brew install lychee                                # ligazóns internas (https://lychee.cli.rs)

# actions-lint.sh
brew install actionlint shellcheck                 # workflows + scripts `run:` embebidos

# openapi-lint.sh
npm install -g @stoplight/spectral-cli @stoplight/spectral-owasp-ruleset  # contrato OpenAPI
```

Fóra de macOS, instala `lychee`, `actionlint` e `shellcheck` co xestor de paquetes do
teu sistema ou dende as súas páxinas de publicación.
