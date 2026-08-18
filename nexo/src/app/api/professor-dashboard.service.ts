import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ProfessorDashboardDTO } from '../core/api.models';

/** Dashboard do professor logado (turmas, aulas, alunos em atenção, feed). */
@Injectable({ providedIn: 'root' })
export class ProfessorDashboardService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/professor`;

  dashboard(): Observable<ProfessorDashboardDTO> {
    return this.http.get<ProfessorDashboardDTO>(`${this.api}/dashboard`);
  }

  /**
   * As matérias que o docente leciona. As telas montavam esse seletor com uma
   * lista fixa no código, o que deixava um professor de Química sem poder lançar
   * nada e oferecia matéria alheia — recusada no salvamento.
   */
  materias(): Observable<string[]> {
    return this.http.get<string[]>(`${this.api}/materias`);
  }

}
