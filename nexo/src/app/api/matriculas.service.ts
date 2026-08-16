import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  MatriculaDTO,
  PageEnvelope,
  StatusDocumentacao,
  StatusMatricula,
} from '../core/api.models';

export interface FiltroMatriculas {
  status?: StatusMatricula;
  turma?: number;
  busca?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class MatriculasService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/matriculas`;

  /** Listagem paginada com filtros aplicados no servidor (antes: filtro client-side em array fixo). */
  listar(filtro: FiltroMatriculas = {}): Observable<PageEnvelope<MatriculaDTO>> {
    let params = new HttpParams();
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.turma != null) params = params.set('turma', filtro.turma);
    if (filtro.busca) params = params.set('busca', filtro.busca);
    if (filtro.page != null) params = params.set('page', filtro.page);
    if (filtro.size != null) params = params.set('size', filtro.size);
    return this.http.get<PageEnvelope<MatriculaDTO>>(this.api, { params });
  }

  detalhar(id: number): Observable<MatriculaDTO> {
    return this.http.get<MatriculaDTO>(`${this.api}/${id}`);
  }

  atualizarDocumentos(id: number, documentacao: StatusDocumentacao): Observable<MatriculaDTO> {
    return this.http.patch<MatriculaDTO>(`${this.api}/${id}/documentos`, { documentacao });
  }

  atualizarStatus(id: number, status: StatusMatricula): Observable<MatriculaDTO> {
    return this.http.patch<MatriculaDTO>(`${this.api}/${id}/status`, { status });
  }

  transferirTurma(id: number, turmaId: number): Observable<MatriculaDTO> {
    return this.http.patch<MatriculaDTO>(`${this.api}/${id}/turma`, { turmaId });
  }

  /** PDF da declaração de matrícula — blob porque a rota exige o Bearer do interceptor. */
  declaracao(id: number): Observable<Blob> {
    return this.http.get(`${this.api}/${id}/declaracao`, { responseType: 'blob' });
  }
}
