import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';
import { resolverFoto } from '../core/avatar';

interface UsuarioApiDTO {
  id: number;
  nome: string;
  cargo: string;
  foto: string | null;
  role: 'ALUNO' | 'PROFESSOR' | 'DIRETOR';
}

/** Perfil do usuário logado: edição de dados e troca de senha (contrato usuarios/me). */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private api = `${environment.apiUrl}/usuarios`;

  /** Edita o nome do usuário logado e atualiza a sessão local. */
  atualizarPerfil(dados: { nome?: string }): Observable<UsuarioApiDTO> {
    return this.http.patch<UsuarioApiDTO>(`${this.api}/me`, dados).pipe(
      tap((u) => this.sincronizar(u)),
    );
  }

  /**
   * Envia a foto de perfil (tirada na câmera ou escolhida da galeria) como arquivo.
   * O componente já reduz a imagem antes de chamar aqui.
   */
  enviarFoto(imagem: Blob): Observable<UsuarioApiDTO> {
    const corpo = new FormData();
    corpo.append('arquivo', imagem, 'perfil.jpg');
    return this.http.post<UsuarioApiDTO>(`${this.api}/me/foto`, corpo).pipe(
      tap((u) => this.sincronizar(u)),
    );
  }

  /** Remove a foto: o usuário volta a aparecer com o avatar padrão. */
  removerFoto(): Observable<UsuarioApiDTO> {
    return this.http.delete<UsuarioApiDTO>(`${this.api}/me/foto`).pipe(
      tap((u) => this.sincronizar(u)),
    );
  }

  private sincronizar(u: UsuarioApiDTO): void {
    this.auth.atualizarUsuarioLocal({ nome: u.nome, foto: resolverFoto(u.foto), cargo: u.cargo });
  }

  /** Troca de senha exige a senha atual (validada no backend). */
  trocarSenha(dados: { senhaAtual: string; novaSenha: string }): Observable<void> {
    return this.http.post<void>(`${this.api}/me/senha`, dados);
  }
}
