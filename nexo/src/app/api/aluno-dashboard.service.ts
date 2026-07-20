import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AlunoDashboardDTO, AlunoNotaDTO } from '../core/api.models';

/** Dashboard de gamificação do aluno logado (XP, ofensiva, ranking, atividades). */
@Injectable({ providedIn: 'root' })
export class AlunoDashboardService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/aluno`;

  dashboard(): Observable<AlunoDashboardDTO> {
    return this.http.get<AlunoDashboardDTO>(`${this.api}/dashboard`);
  }

  /** Notas do aluno logado, por disciplina. */
  notas(): Observable<AlunoNotaDTO[]> {
    return this.http.get<AlunoNotaDTO[]>(`${this.api}/notas`);
  }
}
