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

- `server/` — importación e almacenamento dos datos de contratación, e API de consulta.
- `ui/` — interface web de busca e análise.
- `docs/` — especificacións (`specs/`), funcionalidades (`features/`) e decisións de
  arquitectura (`architecture/`). Consulta [CLAUDE.md](CLAUDE.md) para o fluxo de traballo.
