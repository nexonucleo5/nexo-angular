import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DashboardSecretariaDTO } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class SecretariaService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/secretaria`;

  dashboard(): Observable<DashboardSecretariaDTO> {
    return this.http.get<DashboardSecretariaDTO>(`${this.api}/dashboard`);
  }
}
