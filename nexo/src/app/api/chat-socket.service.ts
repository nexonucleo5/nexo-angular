import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';
import { ChatMensagem } from './chat.service';

/**
 * Cliente WebSocket nativo do chat em tempo real. Conecta com o token JWT na
 * query (o navegador não envia header no WebSocket) e reconecta automaticamente.
 */
@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private auth = inject(AuthService);

  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private fechadoManualmente = false;

  /** Estado da conexão para a UI. */
  readonly conectado = signal(false);
  /** Emite cada mensagem recebida (enviadas por mim ou pelo outro). */
  readonly mensagens$ = new Subject<ChatMensagem>();

  conectar(): void {
    const token = this.auth.token;
    if (!token || this.socket) return;
    this.fechadoManualmente = false;
    this.abrir(token);
  }

  private abrir(token: string): void {
    const url = `${environment.wsUrl}/chat?token=${encodeURIComponent(token)}`;
    const ws = new WebSocket(url);
    this.socket = ws;

    ws.onopen = () => this.conectado.set(true);
    ws.onmessage = (ev) => {
      try {
        this.mensagens$.next(JSON.parse(ev.data) as ChatMensagem);
      } catch {
        /* payload inesperado: ignora */
      }
    };
    ws.onclose = () => {
      this.conectado.set(false);
      this.socket = null;
      if (!this.fechadoManualmente) this.agendarReconexao();
    };
    ws.onerror = () => ws.close();
  }

  private agendarReconexao(): void {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      const token = this.auth.token;
      if (token && !this.fechadoManualmente) this.abrir(token);
    }, 3000);
  }

  enviar(para: number, texto: string): boolean {
    if (this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ para, texto }));
      return true;
    }
    return false;
  }

  desconectar(): void {
    this.fechadoManualmente = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.socket?.close();
    this.socket = null;
    this.conectado.set(false);
  }
}
