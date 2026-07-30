import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, finalize, firstValueFrom, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { resolverFoto } from '../core/avatar';

/** Role no cliente em minúsculo (compatível com os templates/guards existentes). */
export type RoleCliente = 'aluno' | 'professor' | 'diretor';

export interface Usuario {
  id: number;
  nome: string;
  cargo: string;
  foto: string;
  role: RoleCliente;
}

interface UsuarioApi {
  id: number;
  nome: string;
  cargo: string;
  foto: string;
  role: 'ALUNO' | 'PROFESSOR' | 'DIRETOR';
}

/** O refresh token não está aqui de propósito: ele vem em cookie HttpOnly. */
export interface TokenResponse {
  token: string;
  usuario: UsuarioApi;
}

const USUARIO_KEY = 'usuario_nexo';

/**
 * Autenticação real contra o backend Spring (POST /api/auth/login).
 *
 * <p>Onde cada coisa vive, e por quê:
 * <ul>
 *   <li><b>access token</b> — só em memória, neste serviço. Fora de
 *     localStorage/sessionStorage, que qualquer script da página consegue ler. O preço é
 *     que ele se perde no recarregamento; quem cobre isso é {@link restaurarSessao}.</li>
 *   <li><b>refresh token</b> — cookie HttpOnly emitido pelo servidor. O cliente nunca
 *     vê nem manipula o valor: só envia {@code withCredentials} e o navegador anexa o
 *     cookie na rota de refresh. É a credencial de vida longa (7 dias), então é a que
 *     mais importa manter fora do alcance de um XSS.</li>
 *   <li><b>perfil</b> (nome, cargo, foto, role) — localStorage. Não é credencial, é
 *     cache para a UI não piscar no arranque. A role guia só a navegação; o que o
 *     usuário pode de fato fazer é decidido em cada endpoint do servidor.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/auth`;

  public usuarioLogado = signal<Usuario | null>(this.lerUsuarioSalvo());

  /** Ver o comentário da classe: em memória, nunca persistido. */
  private accessToken: string | null = null;

  /** Renovação já em andamento, compartilhada pelos chamadores — ver {@link refresh}. */
  private refreshEmVoo: Observable<TokenResponse> | null = null;

  public login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(
        `${this.api}/login`,
        { login: username, senha: password },
        { withCredentials: true }, // necessário para o navegador aceitar o cookie
      )
      .pipe(tap((resposta) => this.guardarSessao(resposta)));
  }

  /**
   * Retoma a sessão no arranque da aplicação.
   *
   * <p>Como o access token vive em memória, recarregar a página o perde — e o perfil
   * salvo faria os guards liberarem a rota com toda requisição saindo sem
   * {@code Authorization}. O cookie de refresh é o que sobrevive ao reload, então aqui
   * a sessão é reconstruída a partir dele antes de a primeira rota abrir.
   */
  public restaurarSessao(): Promise<void> {
    // Sem perfil salvo não há sessão a retomar; evita um 401 inútil em quem só abriu
    // a tela de login.
    if (!this.usuarioLogado()) return Promise.resolve();

    return firstValueFrom(this.refresh()).then(
      () => undefined,
      () => this.limparSessao(), // cookie ausente, vencido ou revogado
    );
  }

  /**
   * Rotaciona o refresh token — usado pelo interceptor quando o access token expira.
   *
   * <p>Uma só renovação existe por vez e os chamadores concorrentes esperam por ela.
   * Sem isso, os vários GETs que uma tela dispara de uma vez tomavam 401 juntos e cada
   * um chamava /refresh: o primeiro rotacionava o token e os seguintes chegavam ao
   * servidor com o valor antigo. O backend trata reapresentação como vazamento e revoga
   * <b>todas</b> as sessões do usuário, então o que era proteção contra roubo de token
   * virava logout do usuário legítimo a cada expiração.
   */
  public refresh(): Observable<TokenResponse> {
    this.refreshEmVoo ??= this.http
      .post<TokenResponse>(`${this.api}/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((resposta) => this.guardarSessao(resposta)),
        // Libera o slot no fim (sucesso ou erro) para que a próxima expiração do
        // access token rode uma renovação nova em vez de repetir esta.
        finalize(() => (this.refreshEmVoo = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    return this.refreshEmVoo;
  }

  public logout(): void {
    if (this.accessToken) {
      // Melhor esforço: revoga os refresh tokens no servidor e expira o cookie.
      this.http
        .post(`${this.api}/logout`, {}, { withCredentials: true })
        .subscribe({ error: () => {} });
    }
    this.limparSessao();
  }

  public get token(): string | null {
    return this.accessToken;
  }

  /** Atualiza os dados do usuário na sessão local após edição de perfil. */
  public atualizarUsuarioLocal(patch: Partial<Pick<Usuario, 'nome' | 'foto' | 'cargo'>>): void {
    const atual = this.usuarioLogado();
    if (!atual) return;
    const atualizado: Usuario = { ...atual, ...patch };
    localStorage.setItem(USUARIO_KEY, JSON.stringify(atualizado));
    this.usuarioLogado.set(atualizado);
  }

  private guardarSessao(resposta: TokenResponse): void {
    const usuario: Usuario = {
      id: resposta.usuario.id,
      nome: resposta.usuario.nome,
      cargo: resposta.usuario.cargo,
      foto: resolverFoto(resposta.usuario.foto),
      role: resposta.usuario.role.toLowerCase() as RoleCliente,
    };
    this.accessToken = resposta.token;
    localStorage.setItem(USUARIO_KEY, JSON.stringify(usuario));
    this.usuarioLogado.set(usuario);
  }

  private limparSessao(): void {
    this.accessToken = null;
    localStorage.removeItem(USUARIO_KEY);
    this.usuarioLogado.set(null);
  }

  private lerUsuarioSalvo(): Usuario | null {
    try {
      const salvo = localStorage.getItem(USUARIO_KEY);
      return salvo ? (JSON.parse(salvo) as Usuario) : null;
    } catch {
      return null;
    }
  }
}
