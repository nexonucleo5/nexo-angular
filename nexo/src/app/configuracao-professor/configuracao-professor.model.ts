import { AparenciaConfig } from '../configuracoes/settings-store';

export interface ProfessorNotificacoesConfig {
    novasEntregasAlunos: boolean;
    mensagensAlunosResponsaveis: boolean;
    lembreteCorrecaoPendente: boolean;
    notificacoesEmail: boolean;
}

export interface AvaliacaoConfig {
    sugestaoCorrecaoAutomatica: boolean;
    exibirNotasImediatamente: boolean;
    permitirReenvioAtividade: boolean;
}

export interface DisponibilidadeConfig {
    aceitarContatoForaHorario: boolean;
    /** Formato HH:mm — deve ser anterior a horarioFim (validado no client e no backend). */
    horarioInicio: string;
    horarioFim: string;
}

export interface ProfessorPrivacidadeConfig {
    perfilVisivelAlunos: boolean;
    exibirContatoResponsaveis: boolean;
}

export interface ProfessorSettings {
    notificacoes: ProfessorNotificacoesConfig;
    avaliacao: AvaliacaoConfig;
    disponibilidade: DisponibilidadeConfig;
    aparencia: AparenciaConfig;
    privacidade: ProfessorPrivacidadeConfig;
}

/** Defaults idênticos aos do backend (ConfiguracaoService.defaultsPara(PROFESSOR)). */
export const PROFESSOR_DEFAULTS: ProfessorSettings = {
    notificacoes: {
        novasEntregasAlunos: true,
        mensagensAlunosResponsaveis: true,
        lembreteCorrecaoPendente: true,
        notificacoesEmail: true,
    },
    avaliacao: {
        sugestaoCorrecaoAutomatica: false,
        exibirNotasImediatamente: true,
        permitirReenvioAtividade: false,
    },
    disponibilidade: {
        aceitarContatoForaHorario: false,
        horarioInicio: '08:00',
        horarioFim: '18:00',
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
    privacidade: {
        perfilVisivelAlunos: true,
        exibirContatoResponsaveis: false,
    },
};
