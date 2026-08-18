import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ChecklistDTO,
  MatriculaDTO,
  PageEnvelope,
  RematriculaDTO,
  ResultadoLoteDTO,
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

  // ── Rematrícula ────────────────────────────────────────────────────────────

  /** Renova um vínculo para o ano seguinte, promovendo o aluno de série. */
  rematricular(id: number): Observable<RematriculaDTO> {
    return this.http.post<RematriculaDTO>(`${this.api}/${id}/rematricula`, {});
  }

  /** Renova a turma inteira; a resposta diz quem ficou de fora e por quê. */
  rematricularTurma(turmaId: number): Observable<ResultadoLoteDTO> {
    return this.http.post<ResultadoLoteDTO>(`${this.api}/rematricula`, { turmaId });
  }

  // ── Checklist de documentos ────────────────────────────────────────────────

  checklist(id: number): Observable<ChecklistDTO> {
    return this.http.get<ChecklistDTO>(`${this.api}/${id}/documentos/checklist`);
  }

  /** Registrar entrega é estado do documento: PUT, e reenviar não duplica. */
  registrarDocumento(id: number, tipo: string, observacao?: string): Observable<ChecklistDTO> {
    return this.http.put<ChecklistDTO>(`${this.api}/${id}/documentos/${tipo}`, {
      observacao: observacao ?? null,
    });
  }

  removerDocumento(id: number, tipo: string): Observable<ChecklistDTO> {
    return this.http.delete<ChecklistDTO>(`${this.api}/${id}/documentos/${tipo}`);
  }
}
