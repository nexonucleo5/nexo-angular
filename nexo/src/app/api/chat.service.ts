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

export interface ChatAnexo {
  id: string;
  nome: string;
  tipo: string;
  tamanho: number;
}

export interface ChatMensagem {
  id: number | null;
  de: number;
  deNome: string;
  para: number;
  texto: string;
  criadaEm: string;
  anexos?: ChatAnexo[];
}

/**
 * Evento do WebSocket que tira uma mensagem da tela: APAGADA (o autor apagou para
 * todos) ou OCULTADA (eu apaguei só para mim, em outra aba).
 */
export interface ChatMensagemApagada {
  tipo: 'APAGADA' | 'OCULTADA';
  id: number;
  de: number;
  para: number;
}

/** Limites espelhados do backend (ChatService). */
export const CHAT_MAX_ANEXOS = 3;
export const CHAT_TAMANHO_MAXIMO_ANEXO = 5 * 1024 * 1024;

/** Histórico e contatos do chat direto (o envio de texto ao vivo é pelo ChatSocketService). */
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

  /**
   * Mensagem com até 3 anexos (arquivos e/ou áudio gravado). O servidor também a
   * distribui pelo WebSocket, então a tela recebe pelo socket como as demais.
   */
  enviarComAnexos(para: number, texto: string, arquivos: { blob: Blob; nome: string }[]): Observable<ChatMensagem> {
    const form = new FormData();
    if (texto) form.append('texto', texto);
    for (const a of arquivos) form.append('arquivos', a.blob, a.nome);
    return this.http.post<ChatMensagem>(`${this.api}/${para}/mensagens`, form);
  }

  /** Apaga a mensagem para os dois lados (só o autor pode). */
  apagar(mensagemId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/mensagens/${mensagemId}`);
  }

  /** Apaga só para mim (mensagem própria ou recebida); o outro continua vendo. */
  ocultar(mensagemId: number): Observable<void> {
    return this.http.post<void>(`${this.api}/mensagens/${mensagemId}/ocultar`, null);
  }

  /** Bytes do anexo — via HttpClient para levar o token (um <img src> não levaria). */
  baixarAnexo(anexoId: string): Observable<Blob> {
    return this.http.get(`${this.api}/anexos/${anexoId}`, { responseType: 'blob' });
  }

  /** Ticket de uso único para autenticar o handshake do WebSocket. */
  wsTicket(): Observable<{ ticket: string }> {
    return this.http.get<{ ticket: string }>(`${this.api}/ws-ticket`);
  }
}
