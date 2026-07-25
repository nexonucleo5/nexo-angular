import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
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

export interface TokenResponse {
  token: string;
  refreshToken: string;
  usuario: UsuarioApi;
}

const USUARIO_KEY = 'usuario_nexo';
const TOKEN_KEY = 'nexo_token';
const REFRESH_KEY = 'nexo_refresh';

/**
 * Autenticação real contra o backend Spring (POST /api/auth/login).
 * A role vem do payload do backend e é a fonte de verdade dos guards;
 * o token fica em localStorage, mas sua validade é sempre decidida pelo servidor.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/auth`;

  public usuarioLogado = signal<Usuario | null>(this.lerUsuarioSalvo());

  public login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.api}/login`, { login: username, senha: password })
      .pipe(tap((resposta) => this.guardarSessao(resposta)));
  }

  /** Rotaciona o refresh token — usado pelo interceptor quando o access token expira. */
  public refresh(): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${this.api}/refresh`, { refreshToken: this.refreshToken })
      .pipe(tap((resposta) => this.guardarSessao(resposta)));
  }

  public logout(): void {
    if (this.token) {
      // Melhor esforço: revoga os refresh tokens no servidor
      this.http.post(`${this.api}/logout`, {}).subscribe({ error: () => {} });
    }
    localStorage.removeItem(USUARIO_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    this.usuarioLogado.set(null);
  }

  public get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  public get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
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
    localStorage.setItem(TOKEN_KEY, resposta.token);
    localStorage.setItem(REFRESH_KEY, resposta.refreshToken);
    localStorage.setItem(USUARIO_KEY, JSON.stringify(usuario));
    this.usuarioLogado.set(usuario);
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
