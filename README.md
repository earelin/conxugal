# conxugal

[![Actions Lint](https://github.com/earelin/conxugal/actions/workflows/actions-lint.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/actions-lint.yml)
[![Contract fuzz](https://github.com/earelin/conxugal/actions/workflows/contract-fuzz.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/contract-fuzz.yml)
[![Docs Lint](https://github.com/earelin/conxugal/actions/workflows/docs-lint.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/docs-lint.yml)
[![OpenAPI Lint](https://github.com/earelin/conxugal/actions/workflows/openapi-lint.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/openapi-lint.yml)
[![Server CI](https://github.com/earelin/conxugal/actions/workflows/server-ci.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/server-ci.yml)
[![UI Acceptance Tests](https://github.com/earelin/conxugal/actions/workflows/ui-acceptance.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/ui-acceptance.yml)
[![UI CI](https://github.com/earelin/conxugal/actions/workflows/ui-ci.yml/badge.svg)](https://github.com/earelin/conxugal/actions/workflows/ui-ci.yml)

<!-- markdownlint-disable MD033 -->
<img src="ui/public/logo.svg" alt="Logo de conxugal" width="50" height="50" align="left">

Extrae, almacena, analiza e exporta a información de contratos públicos da Xunta de Galicia.

<br clear="left">
<!-- markdownlint-enable MD033 -->

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
- `docs/` — especificacións (`specs/`), funcionalidades (`features/`), decisións de
  arquitectura (`architecture/`, ADRs) e deseño visual de referencia (`design/`), seguindo
  o fluxo de traballo *spec → feature → task*.

## Requisitos

- **[mise](https://mise.jdx.dev)**, que instala **todo o demais**: o JDK, Node e as
  ferramentas dos portos de calidade. As versións de todo o repo están en
  [`.tool-versions`](.tool-versions)
  ([ADR-0026](docs/architecture/0026-pinned-toolchain-with-mise.md)).
- **Docker** con `docker compose` v2 — o único que mise non fornece. Precísano
  PostgreSQL ([ADR-0001](docs/architecture/0001-backend-stack.md)), as probas de
  integración e de aceptación, e as de contrato
  ([ADR-0021](docs/architecture/0021-openapi-contract-testing-with-schemathesis.md)).

Instala mise, engade o seu *hook* ao teu ficheiro de arranque da shell e corre o script
de posta a punto:

```bash
echo 'eval "$(mise activate zsh)"' >> ~/.zshrc   # ou bash, fish...
./scripts/setup-dev-env.sh
```

Fai `mise install`, instala as dependencias de npm e comproba que as ferramentas que
responden no `PATH` son as fixadas e non outras que teñas instaladas.

Se vés do contorno anterior, o script marcará como `shadowed` todo o que resolva a unha
copia allea: as de `brew` (`lychee`, `actionlint`, `shellcheck`, `zizmor`, `vacuum`), as de
`npm i -g` (`markdownlint-cli2`, `maid`) e —sobre todo— **Volta** e **SDKMAN**, que tiña
que ter todo o mundo porque o pin de Node vivía en `ui/package.json`. Non fai falta
desinstalalos: abonda con poñer o `eval` de mise *despois* deles no ficheiro de arranque,
para que as rutas de mise queden diante.

Gradle **non** aprovisiona o JDK por si só — este build non aplica ningún *toolchain
resolver* —, así que `mise install` é obrigatorio antes de compilar o servidor.

## Posta en marcha

Backend (dende `server/`):

```bash
./gradlew run     # arranca o servidor en http://localhost:8080
./gradlew build   # compila, executa as probas e ensambla os módulos
```

UI (dende `ui/`, coas dependencias xa instaladas por `setup-dev-env.sh`):

```bash
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
- `scripts/actions-lint.sh` — os fluxos de GitHub Actions de `.github/workflows/`: sintaxe
  e semántica con `actionlint`, e auditoría de seguridade (referencias a accións sen fixar,
  credenciais persistidas, permisos excesivos) con `zizmor`, que tamén revisa
  `.github/dependabot.yml`.
- `scripts/openapi-lint.sh` — o contrato REST de `docs/api/openapi.yaml`
  ([ADR-0010](docs/architecture/0010-design-first-openapi-contract.md)).

As ferramentas que precisan (`markdownlint-cli2`, `maid`, `lychee`, `actionlint`,
`shellcheck`, `zizmor` e `vacuum`) veñen todas de [`.tool-versions`](.tool-versions), así
que `mise install` chega en calquera sistema operativo. Os scripts avisan se falta algunha.

CI corre estas mesmas versións, de xeito que un porto que pasa en local pasa tamén no
*pull request*.
