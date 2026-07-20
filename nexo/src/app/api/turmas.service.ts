import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ConteudoDTO,
  NotaDTO,
  PresencaAluno,
  ResumoFrequencia,
  TurmaDTO,
} from '../core/api.models';

/** Diário de classe: frequência, conteúdos ministrados e notas por turma. */
@Injectable({ providedIn: 'root' })
export class TurmasService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/turmas`;

  listar(): Observable<TurmaDTO[]> {
    return this.http.get<TurmaDTO[]>(this.api);
  }

  frequenciaDoDia(turmaId: number, data?: string): Observable<PresencaAluno[]> {
    let params = new HttpParams();
    if (data) params = params.set('data', data);
    return this.http.get<PresencaAluno[]>(`${this.api}/${turmaId}/frequencia`, { params });
  }

  /** Salva a chamada do dia (antes: apenas console.log no diario-classe-professor.ts). */
  salvarFrequencia(
    turmaId: number,
    data: string,
    presencas: { alunoId: number; presente: boolean }[],
  ): Observable<ResumoFrequencia> {
    return this.http.post<ResumoFrequencia>(`${this.api}/${turmaId}/frequencia`, { data, presencas });
  }

  historicoConteudos(turmaId: number): Observable<ConteudoDTO[]> {
    return this.http.get<ConteudoDTO[]>(`${this.api}/${turmaId}/conteudos`);
  }

  registrarConteudo(
    turmaId: number,
    conteudo: { titulo: string; descricao?: string; observacoes?: string; data?: string },
  ): Observable<ConteudoDTO> {
    return this.http.post<ConteudoDTO>(`${this.api}/${turmaId}/conteudos`, conteudo);
  }

  notas(turmaId: number, disciplina?: string, periodo?: string): Observable<NotaDTO[]> {
    let params = new HttpParams();
    if (disciplina) params = params.set('disciplina', disciplina);
    if (periodo) params = params.set('periodo', periodo);
    return this.http.get<NotaDTO[]>(`${this.api}/${turmaId}/notas`, { params });
  }
}
