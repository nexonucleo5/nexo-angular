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

/** Desafios do aluno logado (catálogo + progresso; iniciar/concluir persistem). */
@Injectable({ providedIn: 'root' })
export class DesafiosService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/aluno/desafios`;

  listar(): Observable<DesafiosResponse> {
    return this.http.get<DesafiosResponse>(this.api);
  }

  iniciar(id: number): Observable<DesafioDTO> {
    return this.http.post<DesafioDTO>(`${this.api}/${id}/iniciar`, {});
  }

  concluir(id: number): Observable<DesafioDTO> {
    return this.http.post<DesafioDTO>(`${this.api}/${id}/concluir`, {});
  }
}
