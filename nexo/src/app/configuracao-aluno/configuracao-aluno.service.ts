import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { AlunoSettings, ALUNO_DEFAULTS } from './configuracao-aluno.model';

/** Configurações do perfil Aluno (Task 4) — estado em signal + sync via SettingsStore. */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoAlunoService {
    private readonly store = new SettingsStore<AlunoSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'aluno',
        storageKey: 'nexo_settings_aluno',
        defaults: ALUNO_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

    constructor() {
        // O modo foco NÃO é aplicado aqui. A configuração diz "remove distrações
        // durante atividades", e é isso que ela faz: quem liga a classe no body é
        // o App, que sabe a rota atual e só esconde o menu na leitura de conteúdo
        // e no quiz. Aplicando sempre, o aluno ficava sem navegação para chegar às
        // matérias — tinha que desligar o modo foco para conseguir estudar.

        // Acessibilidade: classes globais aplicadas na raiz do documento
        this.store.aoMudar(({ acessibilidade }) => {
            document.documentElement.classList.toggle('fonte-ampliada', acessibilidade.fonteAmpliada);
            document.documentElement.classList.toggle('alto-contraste', acessibilidade.altoContraste);
        });

        // Pede permissão de notificação quando algum aviso está ligado
        this.store.aoMudar(({ notificacoes }) => {
            const querNotificacoes =
                notificacoes.avisoTarefasNovas ||
                notificacoes.lembretesPrazos ||
                notificacoes.mensagensProfessores;
            if (querNotificacoes && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });
    }

    updateSection<K extends keyof AlunoSettings & string>(
        section: K,
        patch: Partial<AlunoSettings[K]>
    ): void {
        this.store.updateSection(section, patch);
    }

    alternarTema(): void {
        this.store.alternarTema();
    }

    resetToDefaults(): void {
        this.store.resetToDefaults();
    }

    notificar(titulo: string, corpo: string): void {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        new Notification(titulo, {
            body: corpo,
            icon: '/assets/icons/icon-192x192.png',
        });
    }
}
