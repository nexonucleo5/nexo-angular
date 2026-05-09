export interface PrivacidadeConfig {
    perfilPublico: boolean;
    exibirNoRanking: boolean;
}

export interface NotificacoesConfig {
    avisoTarefasNovas: boolean;
    lembretesPrazos: boolean;
    mensagensProfessores: boolean;
    notificacoesEmail: boolean;
}

export interface GamificacaoConfig {
    exibirXpTempoReal: boolean;
    animacoesConquista: boolean;
    rankingTurma: boolean;
}

export interface EstudosConfig {
    modoFoco: boolean;
    autoAvancarTarefas: boolean;
    lembreteEstudoDiario: boolean;
}

export interface AparenciaConfig {
    temaEscuro: boolean;
    animacoesInterface: boolean;
}

export interface UserSettings {
    notificacoes: NotificacoesConfig;
    gamificacao: GamificacaoConfig;
    estudos: EstudosConfig;
    aparencia: AparenciaConfig;
    privacidade: PrivacidadeConfig;
}

export const DEFAULT_SETTINGS: UserSettings = {
    notificacoes: {
        avisoTarefasNovas: false,
        lembretesPrazos: false,
        mensagensProfessores: false,
        notificacoesEmail: false,
    },
    gamificacao: {
        exibirXpTempoReal: true,
        animacoesConquista: true,
        rankingTurma: false,
    },
    estudos: {
        modoFoco: false,
        autoAvancarTarefas: false,
        lembreteEstudoDiario: false,
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
    privacidade: {
        perfilPublico: false,
        exibirNoRanking: true,
    },
};