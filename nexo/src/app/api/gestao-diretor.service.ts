import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AlunoRiscoDTO,
  DesempenhoDTO,
  ProfessorMonitorDTO,
  RiscoEvasao,
} from '../core/api.models';

/** Evasão, relatórios e monitoramento — agregações calculadas no servidor. */
@Injectable({ providedIn: 'root' })
export class GestaoDiretorService {
  private http = inject(HttpClient);
  private api = environment.apiUrl;

  riscoEvasao(filtro: { risco?: RiscoEvasao; turma?: number; busca?: string } = {}): Observable<AlunoRiscoDTO[]> {
    let params = new HttpParams();
    if (filtro.risco) params = params.set('risco', filtro.risco);
    if (filtro.turma != null) params = params.set('turma', filtro.turma);
    if (filtro.busca) params = params.set('busca', filtro.busca);
    return this.http.get<AlunoRiscoDTO[]>(`${this.api}/evasao/risco`, { params });
  }

  desempenho(periodo?: string, visao?: string): Observable<DesempenhoDTO> {
    let params = new HttpParams();
    if (periodo) params = params.set('periodo', periodo);
    if (visao) params = params.set('visao', visao);
    return this.http.get<DesempenhoDTO>(`${this.api}/relatorios/desempenho`, { params });
  }

  /** Exportação real de relatório (antes: botão decorativo). Dispara o download no browser. */
  exportarDesempenho(formato: 'pdf' | 'xlsx', periodo?: string, visao?: string): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (periodo) params = params.set('periodo', periodo);
    if (visao) params = params.set('visao', visao);
    return this.http.get(`${this.api}/relatorios/desempenho/export`, { params, responseType: 'blob' });
  }

  monitoramentoProfessores(): Observable<ProfessorMonitorDTO[]> {
    return this.http.get<ProfessorMonitorDTO[]>(`${this.api}/monitoramento/professores`);
  }
}
