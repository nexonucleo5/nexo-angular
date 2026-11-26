import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AlunoCriado, PedidoCadastroDTO } from '../core/api.models';

/**
 * Fila de aprovação de cadastro de aluno: o outro lado de "Exigir aprovação manual de
 * cadastro de aluno" (Configurações → Gestão).
 */
@Injectable({ providedIn: 'root' })
export class CadastrosPendentesService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/cadastros-pendentes`;

  listar(): Observable<PedidoCadastroDTO[]> {
    return this.http.get<PedidoCadastroDTO[]>(this.api);
  }

  /** Só o número, para o painel — não vale trazer a fila inteira para um contador. */
  contagem(): Observable<{ pendentes: number }> {
    return this.http.get<{ pendentes: number }>(`${this.api}/contagem`);
  }

  /** Cria a conta; a resposta traz a senha provisória, que só aparece aqui. */
  aprovar(id: number): Observable<AlunoCriado> {
    return this.http.post<AlunoCriado>(`${this.api}/${id}/aprovar`, {});
  }

  rejeitar(id: number): Observable<PedidoCadastroDTO> {
    return this.http.post<PedidoCadastroDTO>(`${this.api}/${id}/rejeitar`, {});
  }
}
