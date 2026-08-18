import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AlunoDashboardDTO, AlunoNotaDTO, MateriaProgressoDTO } from '../core/api.models';

/** O que é do aluno logado: gamificação, notas, matérias e progresso de estudo. */
@Injectable({ providedIn: 'root' })
export class AlunoDashboardService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/aluno`;

  dashboard(): Observable<AlunoDashboardDTO> {
    return this.http.get<AlunoDashboardDTO>(`${this.api}/dashboard`);
  }

  /** Notas do aluno logado, por disciplina. */
  notas(): Observable<AlunoNotaDTO[]> {
    return this.http.get<AlunoNotaDTO[]>(`${this.api}/notas`);
  }

  /** Matérias da etapa do aluno, com o progresso real dele em cada uma. */
  materias(): Observable<MateriaProgressoDTO[]> {
    return this.http.get<MateriaProgressoDTO[]>(`${this.api}/materias`);
  }

  /** Ids dos conteúdos que o aluno já concluiu numa matéria. */
  conteudosConcluidos(materiaId: number): Observable<number[]> {
    return this.http.get<number[]>(`${this.api}/materias/${materiaId}/concluidos`);
  }

  /** PUT/DELETE: concluir é estado, não evento — repetir não conta duas vezes. */
  concluirConteudo(conteudoId: number): Observable<void> {
    return this.http.put<void>(`${this.api}/conteudos/${conteudoId}/concluido`, {});
  }

  desmarcarConteudo(conteudoId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/conteudos/${conteudoId}/concluido`);
  }
}
