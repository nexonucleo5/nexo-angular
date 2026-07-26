import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CadastroProfessorRequest, ProfessorCriado } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class ProfessoresService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/professores`;

  /** Cadastro de professor — o acesso é gerado no backend, igual ao do aluno. */
  cadastrar(dados: CadastroProfessorRequest): Observable<ProfessorCriado> {
    return this.http.post<ProfessorCriado>(this.api, dados);
  }
}
