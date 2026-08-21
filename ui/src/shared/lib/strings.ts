/**
 * User-facing interface text, in Galician.
 *
 * Kept in a single module so a future i18n feature can lift these into a
 * translation catalogue without restructuring the components that use them.
 */
export const strings = {
  appName: 'conxugal',
  appTagline: 'Contratos públicos da Xunta de Galicia',
  retry: 'Tentar de novo',
  nav: {
    home: 'Inicio',
    about: 'Acerca de',
    adminSection: 'Administración',
    panel: 'Panel',
    users: 'Usuarios',
    organos: 'Órganos',
  },
  roleLabel: {
    ADMIN: 'Administradora',
    USER: 'Usuario',
  },
  userMenu: {
    trigger: 'Abrir o menú da conta',
    logout: 'Pechar sesión',
    logoutError: 'Non foi posible pechar a sesión. Téntao de novo.',
  },
  // Its own top-level namespace rather than any feature's: the paging control
  // lives in `shared/ui` because two other specs' lists take it unchanged, and
  // copy filed under the first feature to need it would have to move the moment
  // the second one did.
  pagination: {
    navLabel: 'Paxinación',
    first: 'Primeira',
    previous: 'Anterior',
    next: 'Seguinte',
    last: 'Última',
    pageLabel: 'Páxina',
    // Takes the total already rendered, so every number in the control is
    // grouped by the one formatter rather than by this module too.
    ofPages: (totalPages: string) => `de ${totalPages}`,
    records: { singular: 'rexistro', plural: 'rexistros' },
  },
  organoPicker: {
    label: 'Órgano',
    placeholder: 'Escolle un órgano',
    searchLabel: 'Buscar un órgano',
    searchPlaceholder: 'Buscar un órgano…',
    inactive: 'Inactivo',
    noMatches: (query: string) => `Ningún órgano coincide con «${query}».`,
    noMatchesHelp: 'Revisa a busca ou baléiraa para ver a árbore completa.',
    // Read aloud when the filter's answer changes. Both leave the query out on
    // purpose: a live region quoting it would be re-read on every keystroke,
    // interrupting itself letter by letter, where these two are read once and
    // only when the answer turns from one into the other.
    matchesAnnounced: 'Hai órganos que coinciden coa busca.',
    noMatchesAnnounced: 'Ningún órgano coincide coa busca.',
    empty: 'Aínda non hai órganos con contratos.',
    emptyHelp: 'Cando se importe o primeiro contrato dun órgano, aparecerá aquí.',
    errorTitle: 'Non se puido cargar a lista de órganos.',
    errorHelp: 'Téntao de novo; se persiste, volve máis tarde.',
  },
  organo: {
    families: {
      contratosMenores: 'Contratos menores',
    },
    tabsLabel: 'Familias de contratos',
    noContracts: 'Non hai contratos para este órgano.',
    // Speaks for what the page can show rather than for what the system stores:
    // a family this build does not know is one it cannot draw, not one the
    // server said nothing about.
    noContractsHelp: 'Non hai ningún contrato deste órgano que se poida amosar aquí.',
    errorTitle: 'Non se puido cargar este órgano.',
    errorHelp: 'Téntao de novo; se persiste, volve máis tarde.',
    notFoundTitle: 'Non atopamos este órgano.',
    notFoundHelp:
      'A ligazón pode estar mal, ou o órgano pode xa non existir no catálogo. Escolle un ' +
      'órgano no selector do panel lateral para seguir.',
  },
  // The contratos menores section's own namespace rather than a key under
  // `organo`: the page above it frames every contract family, so copy filed
  // there would have to move the moment the licitacións section arrives.
  contratosMenores: {
    yearLabel: 'Ano',
    sortLabel: 'Ordenar por',
    // Four entries, which is the whole set: two keys by two directions. Each
    // says the direction in words rather than by an arrow, because the two keys
    // read oppositely — the newest date and the largest amount are both «first»
    // but neither is «highest» in the sense the other is.
    sort: {
      dateDesc: 'Data de publicación (máis recente primeiro)',
      dateAsc: 'Data de publicación (máis antiga primeiro)',
      amountDesc: 'Importe (maior primeiro)',
      amountAsc: 'Importe (menor primeiro)',
    },
    partialTitle: 'Importación en curso',
    partialBody:
      'Aínda se están a importar os contratos deste órgano, así que o que ves aquí está ' +
      'incompleto e medra entre visitas.',
    notUpdatedTitle: 'Este órgano xa non se actualiza',
    notUpdatedBody: 'Os contratos xa importados seguen aquí, pero non se engadirán novos.',

    tableLabel: 'Contratos menores deste órgano',
    columnDate: 'Data',
    columnObxecto: 'Obxecto',
    columnAwardee: 'Adxudicatario',
    columnAmount: 'Importe',
    // Not optional and not a footnote: the thresholds that define a contrato
    // menor are VAT-exclusive, so an unlabelled figure invites exactly the
    // wrong comparison — and so does any total derived from one.
    columnAmountVat: 'IVE incluído',
    columnDuration: 'Duración',
    columnSource: 'Fonte',
    // Said once for the whole column rather than repeated on every row: one
    // statement covers all of them and reads once.
    durationCaveat:
      'A fonte publica a miúdo unha duración por defecto do órgano no canto da do contrato, ' +
      'así que esta columna non se debe ler como un prazo real.',
    // The link is an icon, so its accessible name has to say which contract it
    // opens — the column repeats down the page and «Fonte» alone names none of
    // them.
    sourceLinkLabel: (sourceId: string) =>
      `Ver a publicación na fonte oficial do contrato ${sourceId}`,
    // The object and the duration are the only two values a row can lack, the
    // source publishing neither for some awards. Nothing else needs a marker:
    // a contract without its date, amount or awardee is withheld entirely.
    notPublished: 'Non publicado',
    // Read aloud when the page on screen changes. The rows are swapped under a
    // control that stays put, so nothing else would say that anything happened
    // — a reader who cannot see the table would press «Seguinte» and hear
    // silence. It names the page rather than merely saying it arrived, that
    // being the one thing the swap changed.
    pageAnnounced: (page: string, totalPages: string) =>
      `Páxina ${page} de ${totalPages} dos contratos menores.`,
    errorTitle: 'Non se puideron cargar os contratos.',
    errorHelp: 'Téntao de novo; se persiste, volve máis tarde.',
  },
  home: {
    title: 'Benvido/a a conxugal',
    description:
      'Explora, busca e analiza a información de contratos públicos da Xunta de Galicia ' +
      'publicada en contratosdegalicia.gal.',
  },
  about: {
    title: 'Acerca do proxecto',
    description:
      'conxugal fai accesible e analizable a información de contratación pública da Xunta ' +
      'de Galicia: importa os contratos, almacénaos de forma estruturada e permite buscalos ' +
      'e analizalos nesta interface web.',
  },
  notFound: {
    code: '404',
    title: 'Páxina non atopada',
    description: 'A páxina que buscas non existe ou foi movida.',
    back: 'Volver ao inicio',
  },
  loading: 'Cargando…',
  routeError: {
    title: 'Non foi posible cargar esta sección',
    staleDeployment:
      'Pode que a aplicación se actualizase mentres a tiñas aberta. Téntao de novo para ' +
      'obter a versión máis recente.',
    unexpected: 'Produciuse un erro inesperado nesta sección. Téntao de novo.',
  },
  admin: {
    dashboard: {
      title: 'Panel do sistema',
      subtitle: 'Estado operativo do sistema no momento da consulta.',
      refresh: 'Actualizar',
      checkedAtLabel: 'Comprobado o',
      statusUpTitle: 'Servizo operativo',
      statusUpDescription: 'Todos os compoñentes responden con normalidade.',
      statusDegradedTitle: 'Sistema en modo dexenerado',
      statusDegradedDescription: 'Un ou máis compoñentes non responden con normalidade.',
      serviceLabel: 'Servizo',
      serviceUp: 'Operativo',
      serviceDegraded: 'Dexenerado',
      datastoreLabel: 'Base de datos',
      datastoreReachable: 'Accesible',
      datastoreUnreachable: 'Non accesible',
      systemInfoTitle: 'Información do sistema',
      applicationVersionLabel: 'Versión da aplicación',
      environmentLabel: 'Contorno',
      runtimeLabel: 'Runtime',
      uptimeLabel: 'Tempo de actividade',
      memoryLabel: 'Uso de memoria',
      osLabel: 'Sistema operativo',
      privacyNote: 'A información de estado nunca inclúe credenciais nin cadeas de conexión.',
      errorTitle: 'Non se puido cargar o estado do sistema',
      errorForbidden: 'Non tes permisos para ver esta información.',
      errorGeneric: 'Téntao de novo máis tarde.',
      metrics: {
        title: 'Métricas en tempo real',
        subtitle:
          'Mostras en directo do proceso en execución. Actualízanse soas; non se gardan no servidor.',

        stateConnecting: 'CONECTANDO',
        stateLive: 'EN DIRECTO',
        stateReconnecting: 'RECONECTANDO',

        awaitingFirstSample: 'Agardando a primeira mostra…',
        lastSampleAtPrefix: 'Mostra recibida ás',
        cadenceSuffix: 'cada 5 s',
        lastKnownSampleAtPrefix: 'Última mostra ás',
        timeAgoPrefix: 'hai',
        timeAgoUnit: 's',

        noHistoryYet: 'sen historial aínda',
        samplesUnit: 'mostras',
        notUpdating: 'sen actualizar',
        lastKnownLoad: 'última carga coñecida',
        lastKnownThreads: 'último valor recibido',
        noNewSamples: 'sen novas mostras',
        noValue: '—',

        heapTileLabel: 'Memoria heap',
        systemLoadTileLabel: 'Carga do sistema',
        threadsTileLabel: 'Fíos activos',
        httpTileLabel: 'Peticións HTTP',

        peaksOfHeapPrefix: 'picos do',
        maxOfLoadPrefix: 'máx.',
        peakOfThreadsPrefix: 'pico de',
        recentLoadPrefix: 'carga recente',
        aliveNowPrefix: 'vivos agora',
        sinceLastSamplePrefix: 'desde a mostra anterior',

        memoryGcCardTitle: 'Memoria e recolección de lixo',
        datastorePoolCardTitle: 'Conexións co almacén de datos',
        httpActivityCardTitle: 'Actividade HTTP',

        heapInUseLabel: 'Heap en uso',
        nonHeapMemoryLabel: 'Memoria non heap',
        gcCollectionsLabel: 'Recollidas de lixo',
        gcTimeLabel: 'Tempo en GC',
        sinceStartupLabel: 'Desde o arranque',

        poolUsageLabel: 'Ocupación do pool',
        poolMaxLabel: 'Máximo do pool',
        poolInUseNowLabel: 'En uso agora',
        poolOpenUnit: 'abertas',
        poolConnectionsUnit: 'conexións',
        poolLegendInUse: 'En uso',
        poolLegendIdle: 'Inactivas',
        poolLegendFree: 'Libres',
        poolPrivacyNote: 'Só reconto: sen URL, usuario nin contrasinal.',

        totalRequestsLabel: 'Peticións totais',
        errorResponsesLabel: 'Respostas con erro',
        errorRateLabel: 'Taxa de erro',
        errorRateNormalBadge: 'NORMAL',
        errorRateElevatedBadge: 'ELEVADA',
        errorRateHighBadge: 'ALTA',
        httpAccumulatedNote: 'Acumulados desde o arranque do proceso.',

        privacyNote:
          'As métricas nunca inclúen credenciais, cadeas de conexión nin ningún outro valor secreto.',
        historyNotePrefix: 'O historial das últimas',
        historyNoteSuffix:
          'mostras vive só no navegador: ao recargar a páxina, o panel comeza baleiro.',
      },
    },
    users: {
      title: 'Xestión de usuarios',
      subtitle: 'Xestiona as contas de acceso á aplicación.',
      createButton: 'Novo usuario',
      columnPerson: 'Persoa',
      columnRole: 'Rol',
      columnState: 'Estado',
      columnCreated: 'Creación',
      columnLastLogin: 'Último acceso',
      columnActions: 'Accións',
      stateEnabled: 'Activada',
      stateDisabled: 'Desactivada',
      never: 'Nunca',
      enable: 'Activar',
      disable: 'Desactivar',
      lastAdminTooltip: 'Non se pode desactivar o último administrador activo.',
      lastAdminError: 'Non se pode desactivar o último administrador activo.',
      toggleGenericError: 'Non se puido actualizar o estado da conta. Téntao de novo máis tarde.',
      neverDeletedNote: 'As contas nunca se eliminan; só se desactivan.',
      errorTitle: 'Non se puido cargar a listaxe de usuarios',
      errorForbidden: 'Non tes permisos para ver esta información.',
      errorGeneric: 'Téntao de novo máis tarde.',
      modalTitle: 'Novo usuario',
      emailLabel: 'Correo electrónico',
      emailPlaceholder: 'persoa@dominio.gal',
      emailRequired: 'O correo electrónico é obrigatorio.',
      emailInvalid: 'Introduce un correo electrónico válido.',
      roleFieldLabel: 'Rol',
      cancel: 'Cancelar',
      submit: 'Crear conta',
      duplicateEmailError: 'Xa existe unha conta con este correo electrónico.',
      createGenericError: 'Non se puido crear a conta. Téntao de novo máis tarde.',
      createdTitle: 'Conta creada',
      passwordLabel: 'Contrasinal inicial',
      passwordWarning: 'Este contrasinal non se volverá amosar. Cópiao agora nun lugar seguro.',
      copy: 'Copiar',
      copied: 'Copiado',
      done: 'Feito',
    },
    organos: {
      title: 'Órganos',
      subtitle: 'Organiza o catálogo nunha taxonomía e clasifica os órganos de contratación.',
      treeTitle: 'Taxonomía',
      treeEmpty: 'Aínda non hai termos na taxonomía.',
      unclassified: 'Sen clasificar',
      unclassifiedSubtitle: 'Órganos importados que aínda non teñen termo asignado.',
      columnOrgano: 'Órgano',
      columnState: 'Estado',
      columnContratosMenores: 'Contratos menores',
      columnActions: 'Accións',
      stateActive: 'Activo',
      stateInactive: 'Inactivo',
      termEmpty: 'Este termo aínda non ten órganos.',
      unclassifiedEmpty: 'Non hai órganos sen clasificar.',
      // Both forms of a counted noun travel as one entry, chosen by
      // `singularOrPlural`: a catalogue lifting this out keeps the pair
      // together, and no call site can take one form and forget the other.
      countInTerm: { singular: 'órgano neste termo', plural: 'órganos neste termo' },
      countUnclassified: { singular: 'órgano sen clasificar', plural: 'órganos sen clasificar' },
      errorTitle: 'Non se puido cargar a sección de Órganos',
      errorForbidden: 'Non tes permisos para ver esta información.',
      errorGeneric: 'Téntao de novo máis tarde.',
      import: {
        button: 'Importar catálogo',
        running: 'Importando…',

        successTitle: 'Importación completada',
        // The three counts of the outcome, joined by the component that reads them.
        added: { singular: 'engadido', plural: 'engadidos' },
        refreshed: { singular: 'actualizado', plural: 'actualizados' },
        deactivated: { singular: 'desactivado', plural: 'desactivados' },

        // A normal answer, not a refusal: the guard is system-wide, so the
        // scheduled run holds it just as a second administrator would.
        alreadyRunningTitle: 'Importación en curso',
        alreadyRunning: 'Xa se está a executar unha importación; agarda a que remate.',

        dismiss: 'Pechar',

        // Its own title, distinct from the section's failed-read one: an import
        // that could not run is not a section that could not load.
        errorTitle: 'Non se puido importar o catálogo',
        // The source failure the server names with its own problem type. Saying
        // what is *unchanged* is the point: the catalogue on screen is still the
        // one that was there, not a half-written one.
        errorSource:
          'A fonte de datos non está dispoñible ou devolveu unha resposta inservible. ' +
          'O catálogo, os seus estados e a taxonomía quedan como estaban.',
        // Retrying is not the way out of a refusal, so it does not say to.
        errorForbidden: 'Non tes permisos para lanzar unha importación.',
        errorGeneric: 'Non se puido lanzar a importación. Téntao de novo máis tarde.',
      },
      contratosMenores: {
        scopeNote:
          'Só se importan os contratos menores dos órganos marcados e activos. ' +
          'A primeira importación dun órgano pode durar días.',

        // Prefixed with the Órgano's name at the call site: the switch repeats
        // down the column, so its accessible name has to say which row it is.
        markLabel: 'Importar contratos menores',
        markedTally: { singular: 'marcado para importar', plural: 'marcados para importar' },

        // Four badges for five row states, plus the dash for a row with nothing
        // stored. Two would let a half-loaded Órgano read as up to date.
        badge: {
          marked: 'Marcado',
          partial: 'Parcial',
          imported: 'Importado',
          // Kept visible because unmarking retains the contracts: a dash here
          // would draw an Órgano holding a million rows like one never touched.
          stale: 'Sen actualizar',
          none: '—',
          // Not a badge but the disabled switch's reason, on screen rather than
          // only on hover — a tooltip never fires on a disabled control.
          ineligible: 'Só activos',
        },
        tooltip: {
          none: 'Non se importa ningún contrato menor deste órgano.',
          marked: 'Elixible, pero a primeira importación aínda non gardou ningún contrato.',
          partial:
            'Gárdase o que xa se importou e tamén por onde continuar; ' +
            'a seguinte importación retoma desde aí.',
          // The refresh window reaches back a bounded margin, so a correction older
          // than it needs the historical re-read: *recentes* is the honest claim.
          imported:
            'Está todo o historial publicado pola fonte, e o refresco diario incorpora as ' +
            'novidades e as correccións recentes.',
          // Which of the two stored states it holds, so *resumable* versus
          // *completo* stays reachable without a fifth badge.
          stalePartial:
            'Consérvase o importado, que está incompleto, pero ningunha execución volve a ' +
            'este órgano.',
          staleComplete:
            'Consérvase o historial completo, pero ningunha execución volve a este órgano.',
          ineligible:
            'Só se importan os órganos activos. O interruptor queda desactivado mentres o ' +
            'órgano non estea no catálogo activo.',
        },

        mark: {
          title: 'Importar contratos menores',
          intro: (name: string) => `Vas marcar ${name} para importar os seus contratos menores.`,
          costDuration:
            'A primeira importación descarga todo o historial publicado deste órgano e pode ' +
            'durar días.',
          costGuard:
            'Mentres se executa, non se poderá lanzar ningunha outra importación, nin sequera ' +
            'a do catálogo.',
          costRecentFirst:
            'Empeza polas publicacións máis recentes, así que o máis consultado aparece desde ' +
            'as primeiras horas.',
          costReversible:
            'Podes deixar de importar cando queiras: o xa gardado consérvase e a importación ' +
            'retómase onde quedou.',
          cancel: 'Cancelar',
          submit: 'Marcar e importar',
        },
        // Shared by the mark and the unmark: both write the same attribute, and
        // both fail the same ways.
        write: {
          errorTitle: 'Non se puido gardar a marca',
          notFound: 'Este órgano xa non está no catálogo. Actualiza a sección.',
          forbidden: 'Non tes permisos para cambiar a marca de importación.',
          generic: 'Non se puido gardar a marca. Téntao de novo máis tarde.',
        },

        trigger: {
          button: 'Importar contratos menores',
          running: 'Importando…',
          // Named as the reason both triggers are disabled, so the guard is
          // stated rather than left to be discovered.
          guardHeld: 'Mentres dura, ningunha outra importación pode comezar — nin a do catálogo.',
          errorTitle: 'Non se puido lanzar a importación de contratos menores',
          errorForbidden: 'Non tes permisos para lanzar unha importación.',
          errorGeneric: 'Non se puido lanzar a importación. Téntao de novo máis tarde.',
        },

        run: {
          inProgressTitle: 'Importación de contratos menores en curso',
          succeededTitle: 'Importación de contratos menores rematada',
          partialTitle: 'Importación de contratos menores rematada parcialmente',
          failedTitle: 'A importación de contratos menores fallou',
          // Undrawn by the mockups, which predate the verdict: a multi-day
          // import whose process died and so never wrote its own ending.
          abandonedTitle: 'A importación de contratos menores quedou interrompida',
          // A verdict this build does not know, which means the server moved on
          // without it. Neutral: an unreadable state is not a failed run.
          unknownTitle: 'Non se recoñece o estado desta execución',
          unknownNote:
            'Pode que a aplicación estea desactualizada respecto do servidor. Recarga a páxina ' +
            'para obter a versión máis recente.',

          scopeCount: { singular: 'órgano no alcance', plural: 'órganos no alcance' },
          coveredCount: { singular: 'órgano cuberto', plural: 'órganos cubertos' },
          completedOf: (completed: number, total: number) =>
            `${completed} de ${total} órganos completados`,
          added: { singular: 'contrato engadido', plural: 'contratos engadidos' },
          refreshed: { singular: 'actualizado', plural: 'actualizados' },
          failedOrganos: (count: number) =>
            count === 1 ? 'Fallou 1 órgano' : `Fallaron ${count} órganos`,

          startedAtPrefix: 'Comezou ás',
          finishedAtPrefix: 'rematou ás',
          unfinished: 'sen rematar',
          checkedAgoPrefix: 'Consultado hai',
          checkedAgoUnit: 'min',
          // Past the hour the minutes stop being a freshness anyone can feel.
          checkedAgoHourUnit: 'h',
          checkedJustNow: 'Consultado agora mesmo',
          refresh: 'Actualizar',
          dismiss: 'Pechar',

          succeededNote:
            'O refresco diario volve a estes órganos e incorpora o publicado desde a última ' +
            'execución.',
          failedNote:
            'Os contratos xa gardados consérvanse; a seguinte execución programada retoma onde ' +
            'quedou.',
          abandonedNote:
            'O proceso deixou de avanzar e nunca escribiu o seu remate. Os contratos xa ' +
            'gardados consérvanse; a seguinte execución programada retoma onde quedou.',

          errorTitle: 'Non se puido consultar a execución',
          errorNotFound: 'Xa non existe ningunha execución con ese identificador.',
          errorGeneric: 'Téntao de novo máis tarde.',
        },

        // Neither refusal is an error: nothing broke, and one of them even wrote
        // what it was asked to. Both are grey, and neither carries counts.
        refusal: {
          markKeptTitle: 'Marcouse o órgano, pero non se iniciou ningunha importación',
          noImportTitle: 'Non se iniciou ningunha importación',
          refusedAtPrefix: 'Rexeitada ás',

          guardMark: (name: string) =>
            `Xa se está a executar outra importación. A marca de ${name} quedou gardada.`,
          guardTrigger:
            'Xa se está a executar outra importación, así que non comezou ningunha nova.',
          // Read after either refusal — the mark's and the trigger's — so it names no
          // single Órgano: what the wait costs is freshness, and never a contract.
          guardNote:
            'Non se perde ningún dato, só frescura: a seguinte execución programada cobre os ' +
            'órganos marcados.',

          notEligibleMark: (name: string) =>
            `${name} está inactivo: só se importan os órganos activos e marcados.`,
          notEligibleTrigger:
            'Non hai ningún órgano activo e marcado, así que non había nada que importar.',
          notEligibleNote:
            'O catálogo desactiva un órgano cando deixa de publicarse; volverá ser elixible se ' +
            'reaparece.',
        },
      },
      termo: {
        cancel: 'Cancelar',
        nameLabel: 'Nome',
        nameRequired: 'Escribe un nome para o termo.',
        nameTooLong: (max: number) => `O nome non pode ter máis de ${max} caracteres.`,

        create: 'Novo termo',
        createTitle: 'Novo termo',
        createSubmit: 'Crear termo',
        createNamePlaceholder: 'p. ex. Hospitais e centros de saúde',
        createParentLabel: 'Termo pai',
        createParentPlaceholder: 'Sen termo pai',
        createParentHelp: 'Déixao baleiro para crear un termo raíz.',

        rename: 'Renomear',
        renameTitle: 'Renomear termo',
        renameSubmit: 'Renomear',

        move: 'Mover',
        moveTitle: 'Mover termo',
        moveSubmit: 'Mover',
        moveTermoLabel: 'Termo:',
        moveParentLabel: 'Novo termo pai',
        moveParentRoot: 'Na raíz da taxonomía',

        delete: 'Eliminar',
        deleteTitle: 'Eliminar termo',
        deleteSubmit: 'Eliminar',
        deleteConfirm: (name: string) => `Eliminar «${name}»?`,
        deleteOrganosNote:
          'Os órganos deste termo pasan a «Sen clasificar»; nunca se elimina un órgano.',

        // The four refusals, keyed on the problem type the server sends. They
        // read as different messages on purpose: a cycle and a
        // blocked-by-children delete are both 409, and one shared "conflito"
        // would leave the administrator with nothing to act on.
        cycleTitle: 'Non se pode mover aquí',
        cycleUnderSelf: (termo: string) =>
          `Non podes mover «${termo}» dentro de si mesmo: crearía un ciclo na taxonomía. ` +
          'Elixe un destino que non sexa el mesmo nin un dos seus fillos.',
        cycleUnderChild: (target: string, termo: string) =>
          `«${target}» é un termo fillo de «${termo}»: movelo aquí crearía un ciclo na ` +
          'taxonomía. Elixe un destino que non sexa el mesmo nin un dos seus fillos.',
        hasChildrenTitle: 'Este termo non se pode eliminar',
        hasChildrenOne: (child: string) =>
          `Ten 1 termo fillo («${child}»). Move ou elimina antes os seus termos fillos; ` +
          'despois poderás eliminar este termo.',
        hasChildrenOther: (count: number, children: string) =>
          `Ten ${count} termos fillos («${children}»). Move ou elimina antes os seus ` +
          'termos fillos; despois poderás eliminar este termo.',
        // The server refuses on children this browser's copy of the tree does
        // not have — another administrator added them — and the problem body
        // carries no ids, so this is the one case that cannot name them.
        hasChildrenUnknown:
          'Ten termos fillos que aínda non aparecen aquí. Actualiza a sección, move ou ' +
          'elimina antes os seus termos fillos; despois poderás eliminar este termo.',
        duplicateSiblingName: 'Xa existe un termo irmán con ese nome.',
        notFound: 'Outra persoa eliminou este termo. Actualiza a sección para velo ao día.',
        genericError: 'Non se puido completar a acción. Téntao de novo máis tarde.',
      },
      assign: {
        // The two ways into the same dialog, named for where they are read:
        // from a worklist row it is the órgano that needs a termo, from a
        // term's header it is the termo that needs an órgano.
        fromWorklist: 'Asignar a termo',
        fromTermo: 'Asignar órgano',
        clear: 'Quitar do termo',

        titleTermo: 'Asignar a un termo',
        titleOrgano: 'Asignar órgano',
        submit: 'Asignar',
        organoLabel: 'Órgano',
        termoLabel: 'Termo',
        currently: (placement: string) => `Actualmente: ${placement}`,

        searchLabel: 'Buscar termo',
        searchPlaceholder: 'Buscar termo…',
        noTermoMatches: (query: string) => `Ningún termo coincide con «${query}».`,
        organoPlaceholder: 'Buscar órgano…',
        noOrganoMatches: 'Ningún órgano coincide.',
        inactive: 'Inactivo',

        // Both refusals name what another administrator changed and offer the
        // same way out, since neither is anything this browser can fix by
        // retrying the same write.
        refresh: 'Actualizar',
        organoNotFound: 'Outra persoa eliminou este órgano. Actualiza a sección para velo ao día.',
        termoNotFound:
          'Outra persoa eliminou o termo de destino. Actualiza a sección e escolle outro.',
        genericError: 'Non se puido completar a acción. Téntao de novo máis tarde.',
      },
    },
  },
} as const;
