import { Injectable, signal, computed, effect } from '@angular/core';
import { UserSettings, DEFAULT_SETTINGS } from './configuracoes.model';

const STORAGE_KEY = 'user_settings';

@Injectable({ providedIn: 'root' })
export class ConfiguracoesService {
    private readonly _settings = signal<UserSettings>(this._loadFromStorage());

    /** Snapshot somente-leitura das configurações */
    readonly settings = this._settings.asReadonly();

    readonly isDarkMode = computed(() => this._settings().aparencia.temaEscuro);

    constructor() {
        effect(() => {
            const tema = this._settings().aparencia.temaEscuro ? 'dark' : 'light';
            document.documentElement.setAttribute('data-theme', tema);
        });

        // Animações
        effect(() => {
            const animacoesAtivas = this._settings().aparencia.animacoesInterface;

            document.documentElement.classList.toggle(
                'sem-animacoes',
                !animacoesAtivas
            );
        });

        // Notificações
        effect(() => {
            const prefs = this._settings().notificacoes;
            const querNotificacoes = prefs.avisoTarefasNovas || prefs.lembretesPrazos || prefs.mensagensProfessores;

            if (querNotificacoes && Notification.permission === 'default') {
                Notification.requestPermission();
            }
        });

        // Modo Foco
        effect(() => {
            const focoAtivo = this._settings().estudos.modoFoco;
            document.documentElement.classList.toggle('modo-foco-ativo', focoAtivo);
        });

        // Persistência automática
        effect(() => this._saveToStorage(this._settings()));
    }



    /**
     * Atualiza uma seção específica das configurações de forma imutável.
     * @example updateSection('aparencia', { modoEscuro: true })
     */
    updateSection<K extends keyof UserSettings>(
        section: K,
        patch: Partial<UserSettings[K]>
    ): void {
        this._settings.update((current) => ({
            ...current,
            [section]: { ...current[section], ...patch },
        }));
    }

    /** Restaura todas as configurações para os valores padrão */
    resetToDefaults(): void {
        this._settings.set(structuredClone(DEFAULT_SETTINGS));
    }

    notificar(titulo: string, corpo: string): void {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;

        new Notification(titulo, {
            body: corpo,
            icon: '/assets/icons/icon-192x192.png'
        });
    }

    // ---------------------------------------------------------------------------
    // Persistência
    // ---------------------------------------------------------------------------

    private _loadFromStorage(): UserSettings {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return structuredClone(DEFAULT_SETTINGS);

            // Merge com defaults para garantir campos adicionados em versões futuras
            const stored = JSON.parse(raw) as Partial<UserSettings>;
            return this._mergeWithDefaults(stored);
        } catch {
            return structuredClone(DEFAULT_SETTINGS);
        }
    }

    private _saveToStorage(settings: UserSettings): void {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
        } catch (error) {
            console.warn('[ConfiguracoesService] Falha ao salvar configurações:', error);
        }
    }

    private _mergeWithDefaults(stored: Partial<UserSettings>): UserSettings {
        const defaults = structuredClone(DEFAULT_SETTINGS);
        return {
            notificacoes: { ...defaults.notificacoes, ...stored.notificacoes },
            gamificacao: { ...defaults.gamificacao, ...stored.gamificacao },
            estudos: { ...defaults.estudos, ...stored.estudos },
            aparencia: { ...defaults.aparencia, ...stored.aparencia },
            privacidade: { ...defaults.privacidade, ...stored.privacidade },
        };
    }
}