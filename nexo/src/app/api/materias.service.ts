import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ConteudoMateriaDTO, MateriaDTO } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class MateriasService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/materias`;

  /** Catálogo de matérias da escola. */
  listar(): Observable<MateriaDTO[]> {
    return this.http.get<MateriaDTO[]>(this.api);
  }

  /** Conteúdo/documentos de uma matéria (vazio quando ainda não há nada cadastrado). */
  listarConteudos(materiaId: number): Observable<ConteudoMateriaDTO[]> {
    return this.http.get<ConteudoMateriaDTO[]>(`${this.api}/${materiaId}/conteudos`);
  }
}
