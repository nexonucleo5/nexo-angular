import { AparenciaConfig } from '../configuracoes/settings-store';

export interface DiretorNotificacoesConfig {
    novosCadastrosPendentes: boolean;
    relatoriosPendentes: boolean;
    alertasSistema: boolean;
    notificacoesEmail: boolean;
}

export interface InstitucionalConfig {
    nomeInstituicao: string;
    anoLetivoAtivo: number;
}

export interface GestaoConfig {
    aprovarProfessoresAutomaticamente: boolean;
    exigirAprovacaoManualAluno: boolean;
}

export interface IntegracoesConfig {
    exportarRelatoriosAutomaticamente: boolean;
    sincronizarCalendarioInstitucional: boolean;
}

export interface DiretorPrivacidadeConfig {
    exibirDadosInstituicaoPublicamente: boolean;
}

export interface DiretorSettings {
    notificacoes: DiretorNotificacoesConfig;
    institucional: InstitucionalConfig;
    gestao: GestaoConfig;
    integracoes: IntegracoesConfig;
    aparencia: AparenciaConfig;
    privacidade: DiretorPrivacidadeConfig;
}

/** Defaults idênticos aos do backend (ConfiguracaoService.defaultsPara(DIRETOR)). */
export const DIRETOR_DEFAULTS: DiretorSettings = {
    notificacoes: {
        novosCadastrosPendentes: true,
        relatoriosPendentes: true,
        alertasSistema: true,
        notificacoesEmail: true,
    },
    institucional: {
        nomeInstituicao: 'Colégio Nexo',
        anoLetivoAtivo: 2026,
    },
    gestao: {
        aprovarProfessoresAutomaticamente: false,
        exigirAprovacaoManualAluno: true,
    },
    integracoes: {
        exportarRelatoriosAutomaticamente: false,
        sincronizarCalendarioInstitucional: true,
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
    privacidade: {
        exibirDadosInstituicaoPublicamente: false,
    },
};
