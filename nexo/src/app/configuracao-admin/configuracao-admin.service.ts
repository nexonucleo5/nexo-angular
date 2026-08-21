import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { AdminSettings, ADMIN_DEFAULTS } from './configuracao-admin.model';

/**
 * Configurações do perfil Administrador — seções próprias (contas, catálogo), não
 * as de gestão do diretor. O SettingsStore só sincroniza com a API quando a role
 * do usuário logado bate com a dele.
 */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoAdminService {
    private readonly store = new SettingsStore<AdminSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'admin',
        storageKey: 'nexo_settings_admin',
        defaults: ADMIN_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

    constructor() {
        // Pede permissão de notificação quando algum aviso está ligado
        this.store.aoMudar(({ notificacoes }) => {
            const querNotificacoes =
                notificacoes.novasContasCriadas ||
                notificacoes.contasInativas ||
                notificacoes.conteudoDespublicado;
            if (querNotificacoes && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });
    }

    updateSection<K extends keyof AdminSettings & string>(
        section: K,
        patch: Partial<AdminSettings[K]>
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
