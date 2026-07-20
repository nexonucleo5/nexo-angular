import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AvisoDTO, ConversaDTO, DuvidaDTO, MensagemDTO } from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class ComunicacaoService {
  private http = inject(HttpClient);
  private api = environment.apiUrl;

  listarConversas(caixa: 'entrada' | 'enviada' = 'entrada'): Observable<ConversaDTO[]> {
    const params = new HttpParams().set('caixa', caixa);
    return this.http.get<ConversaDTO[]>(`${this.api}/mensagens`, { params });
  }

  responder(conversaId: number, texto: string): Observable<MensagemDTO> {
    return this.http.post<MensagemDTO>(`${this.api}/mensagens/${conversaId}/responder`, { texto });
  }

  listarAvisos(): Observable<AvisoDTO[]> {
    return this.http.get<AvisoDTO[]>(`${this.api}/avisos`);
  }

  /** Publicar aviso (antes: botão sem handler). */
  publicarAviso(aviso: { titulo: string; conteudo: string; destino?: string }): Observable<AvisoDTO> {
    return this.http.post<AvisoDTO>(`${this.api}/avisos`, aviso);
  }

  listarDuvidas(status?: 'ABERTA' | 'RESPONDIDA'): Observable<DuvidaDTO[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<DuvidaDTO[]>(`${this.api}/duvidas`, { params });
  }

  responderDuvida(id: number, texto: string): Observable<DuvidaDTO> {
    return this.http.post<DuvidaDTO>(`${this.api}/duvidas/${id}/responder`, { texto });
  }
}
