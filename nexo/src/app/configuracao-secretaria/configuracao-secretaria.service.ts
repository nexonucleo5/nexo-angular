import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { SecretariaSettings, SECRETARIA_DEFAULTS } from './configuracao-secretaria.model';

/**
 * Configurações do perfil Secretaria — seções próprias (matrículas, documentos),
 * não as de gestão do diretor. O SettingsStore só sincroniza com a API quando a
 * role do usuário logado bate com a dele.
 */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoSecretariaService {
    private readonly store = new SettingsStore<SecretariaSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'secretaria',
        storageKey: 'nexo_settings_secretaria',
        defaults: SECRETARIA_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

    constructor() {
        // Pede permissão de notificação quando algum aviso está ligado
        this.store.aoMudar(({ notificacoes }) => {
            const querNotificacoes =
                notificacoes.novasMatriculas ||
                notificacoes.documentacaoPendente ||
                notificacoes.transferenciasTurma;
            if (querNotificacoes && 'Notification' in window && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });
    }

    updateSection<K extends keyof SecretariaSettings & string>(
        section: K,
        patch: Partial<SecretariaSettings[K]>
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
