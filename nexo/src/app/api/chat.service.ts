import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChatContato {
  id: number;
  nome: string;
  papel: string;
  foto: string | null;
}

export interface ChatMensagem {
  id: number | null;
  de: number;
  deNome: string;
  para: number;
  texto: string;
  criadaEm: string;
}

/** Histórico e contatos do chat direto (o envio ao vivo é pelo ChatSocketService). */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/chat`;

  contatos(): Observable<ChatContato[]> {
    return this.http.get<ChatContato[]>(`${this.api}/contatos`);
  }

  historico(outroId: number): Observable<ChatMensagem[]> {
    return this.http.get<ChatMensagem[]>(`${this.api}/${outroId}`);
  }
}
