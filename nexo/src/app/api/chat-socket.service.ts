import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth.service';
import { ChatMensagem, ChatService } from './chat.service';

/**
 * Cliente WebSocket nativo do chat em tempo real. O navegador não envia header
 * Authorization em WebSocket, então cada conexão troca o access token por um
 * ticket de uso único (GET /api/chat/ws-ticket) e abre o socket com ele na
 * query — assim a URL nunca carrega uma credencial reutilizável. Reconecta
 * automaticamente, buscando um ticket novo a cada tentativa.
 */
@Injectable({ providedIn: 'root' })
export class ChatSocketService {
  private auth = inject(AuthService);
  private chat = inject(ChatService);

  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private fechadoManualmente = false;
  /** Usuário para o qual o socket atual foi aberto, para detectar troca de conta. */
  private usuarioConectado: number | null = null;

  /** Estado da conexão para a UI. */
  readonly conectado = signal(false);
  /** Emite cada mensagem recebida (enviadas por mim ou pelo outro). */
  readonly mensagens$ = new Subject<ChatMensagem>();

  conectar(): void {
    const usuario = this.auth.usuarioLogado();
    if (!usuario) return;

    // Já conectado com este mesmo usuário: nada a fazer.
    if (this.socket && this.usuarioConectado === usuario.id) return;

    // Trocou de conta na mesma aba (logout/login sem recarregar): o serviço é
    // singleton e o socket antigo continua autenticado como o usuário anterior,
    // o que faria as mensagens saírem como o usuário errado. Derruba antes de abrir.
    if (this.socket) this.fecharSocketAtual();

    this.fechadoManualmente = false;
    this.usuarioConectado = usuario.id;
    this.abrir();
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

  private abrir(): void {
    this.chat.wsTicket().subscribe({
      next: ({ ticket }) => this.abrirComTicket(ticket),
      error: () => this.agendarReconexao(),
    });
  }

  private abrirComTicket(ticket: string): void {
    const url = `${environment.wsUrl}/chat?ticket=${encodeURIComponent(ticket)}`;
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
      // Relê o usuário: se a conta mudou nesse intervalo, reconecta como a nova.
      const usuario = this.auth.usuarioLogado();
      if (usuario && !this.fechadoManualmente) {
        this.usuarioConectado = usuario.id;
        this.abrir();
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
    this.usuarioConectado = null;
    this.conectado.set(false);
  }
}
