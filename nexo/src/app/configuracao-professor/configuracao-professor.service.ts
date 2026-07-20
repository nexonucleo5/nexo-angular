import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { ProfessorSettings, PROFESSOR_DEFAULTS } from './configuracao-professor.model';

/** Configurações do perfil Professor (Task 4) — estado em signal + sync via SettingsStore. */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoProfessorService {
    private readonly store = new SettingsStore<ProfessorSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'professor',
        storageKey: 'nexo_settings_professor',
        defaults: PROFESSOR_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

    constructor() {
        // Pede permissão de notificação quando algum aviso está ligado
        this.store.aoMudar(({ notificacoes }) => {
            const querNotificacoes =
                notificacoes.novasEntregasAlunos ||
                notificacoes.mensagensAlunosResponsaveis ||
                notificacoes.lembreteCorrecaoPendente;
            if (querNotificacoes && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });
    }

    updateSection<K extends keyof ProfessorSettings & string>(
        section: K,
        patch: Partial<ProfessorSettings[K]>
    ): void {
        this.store.updateSection(section, patch);
    }

    alternarTema(): void {
        this.store.alternarTema();
    }

    resetToDefaults(): void {
        this.store.resetToDefaults();
    }
}
