import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DesafioDTO {
  id: number;
  titulo: string;
  materia: string;
  nivel: 'FACIL' | 'MEDIO' | 'DIFICIL';
  xp: number;
  tempoMin: number;
  status: 'ABERTO' | 'PROGRESSO' | 'CONCLUIDO';
  progresso: number;
  acertos: number | null;
  totalPerguntas: number | null;
}

export interface DesafiosStats {
  concluidos: number;
  total: number;
  taxaSucesso: number;
  sequenciaDias: number;
}

export interface DesafiosResponse {
  stats: DesafiosStats;
  desafios: DesafioDTO[];
}

export interface QuizPerguntaDTO {
  id: number;
  enunciado: string;
  alternativas: string[];
  /** Só vem preenchido quando o desafio já foi concluído (modo revisão). */
  respostaCorreta: number | null;
}

export interface RespostaItem {
  perguntaId: number;
  alternativaEscolhida: number;
}

export interface QuizResultadoDTO {
  acertos: number;
  totalPerguntas: number;
  xpGanho: number;
  status: string;
}

/** Desafios do aluno logado (catálogo + progresso + quiz). */
@Injectable({ providedIn: 'root' })
export class DesafiosService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/aluno/desafios`;

  listar(): Observable<DesafiosResponse> {
    return this.http.get<DesafiosResponse>(this.api);
  }

  obter(id: number): Observable<DesafioDTO> {
    return this.http.get<DesafioDTO>(`${this.api}/${id}`);
  }

  iniciar(id: number): Observable<DesafioDTO> {
    return this.http.post<DesafioDTO>(`${this.api}/${id}/iniciar`, {});
  }

  concluir(id: number): Observable<DesafioDTO> {
    return this.http.post<DesafioDTO>(`${this.api}/${id}/concluir`, {});
  }

  listarPerguntas(id: number): Observable<QuizPerguntaDTO[]> {
    return this.http.get<QuizPerguntaDTO[]>(`${this.api}/${id}/quiz`);
  }

  finalizarQuiz(id: number, respostas: RespostaItem[]): Observable<QuizResultadoDTO> {
    return this.http.post<QuizResultadoDTO>(`${this.api}/${id}/quiz/finalizar`, { respostas });
  }
}
