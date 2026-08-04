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
