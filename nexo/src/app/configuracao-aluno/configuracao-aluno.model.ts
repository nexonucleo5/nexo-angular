import { AparenciaConfig } from '../configuracoes/settings-store';

export interface AlunoNotificacoesConfig {
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

export interface AcessibilidadeConfig {
    fonteAmpliada: boolean;
    altoContraste: boolean;
    leituraVozAlta: boolean;
}

export interface AlunoPrivacidadeConfig {
    perfilPublico: boolean;
    exibirNoRanking: boolean;
    visivelResponsaveis: boolean;
}

export interface AlunoSettings {
    notificacoes: AlunoNotificacoesConfig;
    gamificacao: GamificacaoConfig;
    estudos: EstudosConfig;
    aparencia: AparenciaConfig;
    acessibilidade: AcessibilidadeConfig;
    privacidade: AlunoPrivacidadeConfig;
}

/** Defaults idênticos aos do backend (ConfiguracaoService.defaultsPara(ALUNO)). */
export const ALUNO_DEFAULTS: AlunoSettings = {
    notificacoes: {
        avisoTarefasNovas: true,
        lembretesPrazos: true,
        mensagensProfessores: false,
        notificacoesEmail: true,
    },
    gamificacao: {
        exibirXpTempoReal: true,
        animacoesConquista: true,
        rankingTurma: true,
    },
    estudos: {
        modoFoco: false,
        autoAvancarTarefas: true,
        lembreteEstudoDiario: true,
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
    acessibilidade: {
        fonteAmpliada: false,
        altoContraste: false,
        leituraVozAlta: false,
    },
    privacidade: {
        perfilPublico: true,
        exibirNoRanking: true,
        visivelResponsaveis: true,
    },
};
