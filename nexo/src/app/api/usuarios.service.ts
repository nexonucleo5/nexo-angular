import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';

interface UsuarioApiDTO {
  id: number;
  nome: string;
  cargo: string;
  foto: string;
  role: 'ALUNO' | 'PROFESSOR' | 'DIRETOR';
}

/** Perfil do usuário logado: edição de dados e troca de senha (contrato usuarios/me). */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);
  private api = `${environment.apiUrl}/usuarios`;

  /** Edita nome/foto do usuário logado e atualiza a sessão local. */
  atualizarPerfil(dados: { nome?: string; foto?: string }): Observable<UsuarioApiDTO> {
    return this.http.patch<UsuarioApiDTO>(`${this.api}/me`, dados).pipe(
      tap((u) => this.auth.atualizarUsuarioLocal({ nome: u.nome, foto: u.foto, cargo: u.cargo })),
    );
  }

  /** Troca de senha exige a senha atual (validada no backend). */
  trocarSenha(dados: { senhaAtual: string; novaSenha: string }): Observable<void> {
    return this.http.post<void>(`${this.api}/me/senha`, dados);
  }
}
