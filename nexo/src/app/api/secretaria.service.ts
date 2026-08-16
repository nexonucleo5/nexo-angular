import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DashboardSecretariaDTO, OcupacaoTurmaDTO, PendenciaDTO } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class SecretariaService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/secretaria`;

  dashboard(): Observable<DashboardSecretariaDTO> {
    return this.http.get<DashboardSecretariaDTO>(`${this.api}/dashboard`);
  }

  pendencias(limite = 8): Observable<PendenciaDTO[]> {
    const params = new HttpParams().set('limite', limite);
    return this.http.get<PendenciaDTO[]>(`${this.api}/pendencias`, { params });
  }

  ocupacao(): Observable<OcupacaoTurmaDTO[]> {
    return this.http.get<OcupacaoTurmaDTO[]>(`${this.api}/turmas/ocupacao`);
  }
}
