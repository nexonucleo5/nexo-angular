import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SettingsStore } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';
import { DiretorSettings, DIRETOR_DEFAULTS } from '../configuracao-diretor/configuracao-diretor.model';

/**
 * Configurações do perfil Secretaria. O shape é o mesmo do diretor (notificações
 * administrativas, aparência) — o que muda é a role do store: o SettingsStore só
 * sincroniza com a API quando a role do usuário logado bate com a dele.
 */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoSecretariaService {
    private readonly store = new SettingsStore<DiretorSettings>({
        http: inject(HttpClient),
        auth: inject(AuthService),
        role: 'secretaria',
        storageKey: 'nexo_settings_secretaria',
        defaults: DIRETOR_DEFAULTS,
    });

    readonly settings = this.store.settings;
    readonly isDarkMode = this.store.isDarkMode;

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
