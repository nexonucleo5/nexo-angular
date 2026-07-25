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
  /** Token com que o socket atual foi autenticado, para detectar troca de usuário. */
  private tokenConectado: string | null = null;

  /** Estado da conexão para a UI. */
  readonly conectado = signal(false);
  /** Emite cada mensagem recebida (enviadas por mim ou pelo outro). */
  readonly mensagens$ = new Subject<ChatMensagem>();

  conectar(): void {
    const token = this.auth.token;
    if (!token) return;

    // Já conectado com este mesmo usuário: nada a fazer.
    if (this.socket && this.tokenConectado === token) return;

    // Trocou de conta na mesma aba (logout/login sem recarregar): o serviço é
    // singleton e o socket antigo continua autenticado com o token anterior, o
    // que faria as mensagens saírem como o usuário errado. Derruba antes de abrir.
    if (this.socket) this.fecharSocketAtual();

    this.fechadoManualmente = false;
    this.tokenConectado = token;
    this.abrir(token);
  }

  /** Fecha o socket vigente sem disparar a reconexão automática dele. */
  private fecharSocketAtual(): void {
    const antigo = this.socket;
    this.socket = null;
    this.conectado.set(false);
    if (!antigo) return;
    antigo.onclose = null; // o onclose padrão agendaria uma reconexão indevida
    antigo.onerror = null;
    antigo.onmessage = null;
    try {
      antigo.close();
    } catch {
      /* socket já encerrado */
    }
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
      // Relê o token: se o usuário mudou nesse intervalo, reconecta como o novo.
      const token = this.auth.token;
      if (token && !this.fechadoManualmente) {
        this.tokenConectado = token;
        this.abrir(token);
      }
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
    this.tokenConectado = null;
    this.conectado.set(false);
  }
}
