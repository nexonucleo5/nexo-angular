import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Recuperação de senha: os dois endpoints públicos de /api/auth/senha.
 *
 * <p>Fora do AuthService de propósito — este fluxo é para quem <b>não</b> está autenticado,
 * e não toca em token, sessão nem estado de usuário logado.
 */
@Injectable({ providedIn: 'root' })
export class RecuperacaoSenhaService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/auth/senha`;

  /**
   * Pede o link de redefinição. Responde 204 mesmo para login que não existe: o servidor
   * não revela quem está cadastrado, e a tela precisa se comportar do mesmo jeito — ver
   * o comentário em RecuperarSenha.enviar().
   */
  solicitar(login: string): Observable<void> {
    return this.http.post<void>(`${this.api}/esqueci`, { login });
  }

  /** Consome o token recebido por e-mail e grava a senha nova. */
  redefinir(token: string, novaSenha: string): Observable<void> {
    return this.http.post<void>(`${this.api}/redefinir`, { token, novaSenha });
  }
}
