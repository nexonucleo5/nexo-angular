import { AparenciaConfig } from '../configuracoes/settings-store';

export interface SecretariaNotificacoesConfig {
    novasMatriculas: boolean;
    documentacaoPendente: boolean;
    transferenciasTurma: boolean;
    notificacoesEmail: boolean;
}

export interface DocumentosConfig {
    /** Dias de validade impressos na declaração de matrícula. */
    validadeDeclaracaoDias: number;
    /** Acima deste tamanho a fila de pendências vira alerta no painel. */
    avisarFilaAcimaDe: number;
}

export interface SecretariaSettings {
    notificacoes: SecretariaNotificacoesConfig;
    documentos: DocumentosConfig;
    aparencia: AparenciaConfig;
}

/** Defaults idênticos aos do backend (ConfiguracaoService.defaultsPara(SECRETARIA)). */
export const SECRETARIA_DEFAULTS: SecretariaSettings = {
    notificacoes: {
        novasMatriculas: true,
        documentacaoPendente: true,
        transferenciasTurma: true,
        notificacoesEmail: true,
    },
    documentos: {
        validadeDeclaracaoDias: 30,
        avisarFilaAcimaDe: 10,
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
};
