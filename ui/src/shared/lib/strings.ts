/**
 * User-facing interface text, in Galician.
 *
 * Kept in a single module so a future i18n feature can lift these into a
 * translation catalogue without restructuring the components that use them.
 */
export const strings = {
  appName: 'conxugal',
  appTagline: 'Contratos públicos da Xunta de Galicia',
  nav: {
    home: 'Inicio',
    about: 'Acerca de',
    adminSection: 'Administración',
    panel: 'Panel',
    users: 'Usuarios',
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
  },
} as const;
