import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { DiretorSettings, DIRETOR_DEFAULTS } from './configuracao-diretor.model';

/** Configurações do perfil Diretor (Task 4) — estado em signal + sync via SettingsStore. */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoDiretorService {
    private readonly store = new SettingsStore<DiretorSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'diretor',
        storageKey: 'nexo_settings_diretor',
        defaults: DIRETOR_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

    constructor() {
        // Pede permissão de notificação quando algum aviso está ligado
        this.store.aoMudar(({ notificacoes }) => {
            const querNotificacoes =
                notificacoes.novosCadastrosPendentes ||
                notificacoes.relatoriosPendentes ||
                notificacoes.alertasSistema;
            if (querNotificacoes && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });
    }

    updateSection<K extends keyof DiretorSettings & string>(
        section: K,
        patch: Partial<DiretorSettings[K]>
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
