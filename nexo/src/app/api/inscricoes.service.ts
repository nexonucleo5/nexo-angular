import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InscricaoDTO, PageEnvelope } from '../core/api.models';

export interface FiltroInscricoes {
  ativo?: boolean;
  turma?: number;
  busca?: string;
  page?: number;
  size?: number;
}

/**
 * Inscrições de alunos nas turmas de estudo — o que sobrou do antigo
 * /api/matriculas. Documentos, declaração, rematrícula e status de vínculo saíram
 * junto com a matrícula: essa é a vida escolar, e ela mora no sistema de aula.
 */
@Injectable({ providedIn: 'root' })
export class InscricoesService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/inscricoes`;

  /** Listagem paginada com os filtros aplicados no servidor. */
  listar(filtro: FiltroInscricoes = {}): Observable<PageEnvelope<InscricaoDTO>> {
    let params = new HttpParams();
    if (filtro.ativo != null) params = params.set('ativo', filtro.ativo);
    if (filtro.turma != null) params = params.set('turma', filtro.turma);
    if (filtro.busca) params = params.set('busca', filtro.busca);
    if (filtro.page != null) params = params.set('page', filtro.page);
    if (filtro.size != null) params = params.set('size', filtro.size);
    return this.http.get<PageEnvelope<InscricaoDTO>>(this.api, { params });
  }

  detalhar(id: number): Observable<InscricaoDTO> {
    return this.http.get<InscricaoDTO>(`${this.api}/${id}`);
  }

  /** Liga/desliga o acesso ao conteúdo da turma; o progresso do aluno permanece. */
  atualizarAtivo(id: number, ativo: boolean): Observable<InscricaoDTO> {
    return this.http.patch<InscricaoDTO>(`${this.api}/${id}/ativo`, { ativo });
  }

  transferirTurma(id: number, turmaId: number): Observable<InscricaoDTO> {
    return this.http.patch<InscricaoDTO>(`${this.api}/${id}/turma`, { turmaId });
  }
}
