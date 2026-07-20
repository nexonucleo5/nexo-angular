import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AvaliacaoDTO, QuestaoDTO, StatusAvaliacao } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class AvaliacoesService {
  private http = inject(HttpClient);
  private api = environment.apiUrl;

  listar(filtro: { turma?: number; status?: StatusAvaliacao } = {}): Observable<AvaliacaoDTO[]> {
    let params = new HttpParams();
    if (filtro.turma != null) params = params.set('turma', filtro.turma);
    if (filtro.status) params = params.set('status', filtro.status);
    return this.http.get<AvaliacaoDTO[]>(`${this.api}/avaliacoes`, { params });
  }

  criar(avaliacao: {
    titulo: string;
    disciplina?: string;
    turmaId?: number;
    tipo?: string;
    data?: string;
  }): Observable<AvaliacaoDTO> {
    return this.http.post<AvaliacaoDTO>(`${this.api}/avaliacoes`, avaliacao);
  }

  filaCorrecao(): Observable<AvaliacaoDTO[]> {
    return this.http.get<AvaliacaoDTO[]>(`${this.api}/avaliacoes/fila-correcao`);
  }

  listarQuestoes(): Observable<QuestaoDTO[]> {
    return this.http.get<QuestaoDTO[]>(`${this.api}/questoes`);
  }

  criarQuestao(questao: {
    enunciado: string;
    disciplina?: string;
    tipo?: 'OBJETIVA' | 'DISSERTATIVA';
    dificuldade?: 'FACIL' | 'MEDIA' | 'DIFICIL';
  }): Observable<QuestaoDTO> {
    return this.http.post<QuestaoDTO>(`${this.api}/questoes`, questao);
  }

  atualizarQuestao(id: number, questao: {
    enunciado?: string;
    disciplina?: string;
    tipo?: 'OBJETIVA' | 'DISSERTATIVA';
    dificuldade?: 'FACIL' | 'MEDIA' | 'DIFICIL';
  }): Observable<QuestaoDTO> {
    return this.http.put<QuestaoDTO>(`${this.api}/questoes/${id}`, questao);
  }

  excluirQuestao(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/questoes/${id}`);
  }
}
