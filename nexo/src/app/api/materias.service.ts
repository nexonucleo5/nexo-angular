import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { MateriaDTO } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class MateriasService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/materias`;

  /** Catálogo de matérias da escola. */
  listar(): Observable<MateriaDTO[]> {
    return this.http.get<MateriaDTO[]>(this.api);
  }
}
