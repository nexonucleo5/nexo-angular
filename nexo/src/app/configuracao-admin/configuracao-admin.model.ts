import { AparenciaConfig } from '../configuracoes/settings-store';

export interface AdminNotificacoesConfig {
    novasContasCriadas: boolean;
    contasInativas: boolean;
    conteudoDespublicado: boolean;
    notificacoesEmail: boolean;
}

/**
 * Substituiu a seção "documentos" da antiga secretaria, que guardava validade de
 * declaração de matrícula e tamanho da fila de pendências. Nada disso existe mais:
 * o que este perfil administra é catálogo.
 */
export interface CatalogoConfig {
    /** Conteúdo recém-cadastrado já nasce visível para o aluno. */
    publicarConteudoNovoAutomaticamente: boolean;
    /** Abaixo deste número de conteúdos, a matéria vira alerta no painel. */
    avisarMateriaComMenosDeNConteudos: number;
}

export interface AdminSettings {
    notificacoes: AdminNotificacoesConfig;
    catalogo: CatalogoConfig;
    aparencia: AparenciaConfig;
}

/** Defaults idênticos aos do backend (ConfiguracaoService.defaultsPara(ADMIN)). */
export const ADMIN_DEFAULTS: AdminSettings = {
    notificacoes: {
        novasContasCriadas: true,
        contasInativas: true,
        conteudoDespublicado: true,
        notificacoesEmail: true,
    },
    catalogo: {
        publicarConteudoNovoAutomaticamente: true,
        avisarMateriaComMenosDeNConteudos: 3,
    },
    aparencia: {
        temaEscuro: true,
        animacoesInterface: true,
    },
};
