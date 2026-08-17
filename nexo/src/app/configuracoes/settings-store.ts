import { HttpClient } from '@angular/common/http';
import { Signal, WritableSignal, computed, effect, signal } from '@angular/core';
import { AuthService, RoleCliente } from '../services/auth.service';
import { environment } from '../../environments/environment';

const TEMA_KEY = 'nexo_tema';
const DEBOUNCE_MS = 600;

/** Seção de Aparência — comum aos três perfis (Aluno, Professor, Diretor). */
export interface AparenciaConfig {
    temaEscuro: boolean;
    animacoesInterface: boolean;
}

/**
 * Aplica o tema no documento e guarda em cache compartilhado (lido antes do login).
 *
 * <p>A classe `trocando-tema` corta as transições durante a virada (ver styles.scss):
 * cada caixa tem a sua própria duração de transição de cor, então sem isso o tema
 * entrava em ondas — header, depois cards, depois bordas. Com as transições cortadas
 * tudo repinta no mesmo frame, e elas voltam no frame seguinte para o hover continuar
 * suave. Os dois rAF encadeados são de propósito: um só ainda cai dentro do frame em
 * que o estilo é recalculado, e a onda voltava.
 */
export function aplicarTema(escuro: boolean): void {
    const raiz = document.documentElement;
    raiz.classList.add('trocando-tema');
    raiz.setAttribute('data-theme', escuro ? 'dark' : 'light');
    requestAnimationFrame(() =>
        requestAnimationFrame(() => raiz.classList.remove('trocando-tema'))
    );
    try {
        localStorage.setItem(TEMA_KEY, escuro ? 'dark' : 'light');
    } catch {
        /* storage indisponível: só aplica no DOM */
    }
}

/** Último tema aplicado (default: escuro, igual ao default do backend). */
export function temaSalvoEscuro(): boolean {
    try {
        return localStorage.getItem(TEMA_KEY) !== 'light';
    } catch {
        return true;
    }
}

export interface SettingsStoreOpts<T> {
    http: HttpClient;
    auth: AuthService;
    /** Só sincroniza com a API quando o usuário logado é deste perfil. */
    role: RoleCliente;
    storageKey: string;
    defaults: T;
}

/**
 * Utilitário único de configurações por perfil (Task 4) — extrai a lógica de
 * load/save/merge do localStorage antes repetida, mais a sincronização com a API
 * decidida na Task 2:
 * - localStorage é cache local (tema aplicado antes do primeiro round-trip);
 * - leitura inicial faz GET /api/configuracoes com "servidor vence";
 * - cada updateSection agenda PATCH /api/configuracoes/{secao} com debounce;
 * - API indisponível → segue com o cache local.
 *
 * Deve ser construído dentro de um contexto de injeção (constructor/field do
 * service de cada perfil), pois registra effect()s.
 */
export class SettingsStore<T extends { aparencia: AparenciaConfig }> {
    private readonly _settings: WritableSignal<T>;
    private readonly _timersPatch = new Map<string, ReturnType<typeof setTimeout>>();
    private _sincronizadoPara: number | null = null;
    private readonly api = `${environment.apiUrl}/configuracoes`;

    readonly settings: Signal<T>;
    readonly isDarkMode: Signal<boolean>;

    constructor(private readonly opts: SettingsStoreOpts<T>) {
        this._settings = signal(this._loadFromStorage());
        this.settings = this._settings.asReadonly();
        this.isDarkMode = computed(() => this._settings().aparencia.temaEscuro);

        // Cache local (aplica o tema imediatamente no próximo carregamento)
        effect(() => this._saveToStorage(this._settings()));

        // Aparência é comum aos três perfis: tema + animações da interface
        effect(() => aplicarTema(this._settings().aparencia.temaEscuro));
        effect(() =>
            document.documentElement.classList.toggle(
                'sem-animacoes',
                !this._settings().aparencia.animacoesInterface
            )
        );

        // Sincroniza com o servidor quando há sessão ativa deste perfil
        // (re-sincroniza a cada login; servidor vence em conflito)
        effect(() => {
            const usuario = this.opts.auth.usuarioLogado();
            if (usuario && usuario.role === this.opts.role && this._sincronizadoPara !== usuario.id) {
                this._sincronizadoPara = usuario.id;
                this._syncFromApi();
            }
        });
    }

    /**
     * Registra um efeito derivado das configurações do perfil
     * (ex.: modo foco do Aluno). Chamar apenas no constructor do service.
     */
    aoMudar(fn: (settings: T) => void): void {
        effect(() => fn(this._settings()));
    }

    /**
     * Atualiza uma seção de forma imutável e agenda o PATCH correspondente.
     * @example store.updateSection('aparencia', { temaEscuro: true })
     */
    updateSection<K extends keyof T & string>(section: K, patch: Partial<T[K]>): void {
        this._settings.update((atual) => ({
            ...atual,
            [section]: { ...atual[section], ...patch },
        }));
        this._agendarPatch(section);
    }

    /** Restaura todas as configurações para os valores padrão do perfil. */
    resetToDefaults(): void {
        this._settings.set(structuredClone(this.opts.defaults));
        Object.keys(this.opts.defaults).forEach((secao) =>
            this._agendarPatch(secao as keyof T & string)
        );
    }

    alternarTema(): void {
        this.updateSection('aparencia', {
            temaEscuro: !this._settings().aparencia.temaEscuro,
        } as Partial<T['aparencia']>);
    }

    // ---------------------------------------------------------------------------
    // Sincronização com a API
    // ---------------------------------------------------------------------------

    private _syncFromApi(): void {
        this.opts.http.get<Record<string, unknown>>(this.api).subscribe({
            next: (servidor) => this._settings.set(this._mergeWithDefaults(servidor)),
            // API indisponível: mantém o cache local (offline-first para itens não críticos)
            error: () => {},
        });
    }

    private _agendarPatch(secao: keyof T & string): void {
        const usuario = this.opts.auth.usuarioLogado();
        if (!usuario || usuario.role !== this.opts.role) return;

        const timerAnterior = this._timersPatch.get(secao);
        if (timerAnterior) clearTimeout(timerAnterior);

        this._timersPatch.set(secao, setTimeout(() => {
            this._timersPatch.delete(secao);
            this.opts.http.patch(`${this.api}/${secao}`, this._settings()[secao]).subscribe({
                error: (erro) =>
                    console.warn(`[SettingsStore:${this.opts.role}] Falha ao sincronizar seção "${secao}":`, erro),
            });
        }, DEBOUNCE_MS));
    }

    // ---------------------------------------------------------------------------
    // Persistência local (cache)
    // ---------------------------------------------------------------------------

    private _loadFromStorage(): T {
        try {
            const raw = localStorage.getItem(this.opts.storageKey);
            if (!raw) return structuredClone(this.opts.defaults);
            return this._mergeWithDefaults(JSON.parse(raw));
        } catch {
            return structuredClone(this.opts.defaults);
        }
    }

    private _saveToStorage(settings: T): void {
        try {
            localStorage.setItem(this.opts.storageKey, JSON.stringify(settings));
        } catch (error) {
            console.warn(`[SettingsStore:${this.opts.role}] Falha ao salvar configurações:`, error);
        }
    }

    /** Merge raso por seção com os defaults — garante campos novos em versões futuras. */
    private _mergeWithDefaults(dados: unknown): T {
        const resultado = structuredClone(this.opts.defaults) as unknown as Record<string, Record<string, unknown>>;
        if (dados && typeof dados === 'object') {
            for (const secao of Object.keys(resultado)) {
                const valores = (dados as Record<string, unknown>)[secao];
                if (valores && typeof valores === 'object') {
                    Object.assign(resultado[secao], valores);
                }
            }
        }
        return resultado as unknown as T;
    }
}
