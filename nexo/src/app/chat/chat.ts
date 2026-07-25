import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ChatContato, ChatMensagem, ChatService } from '../api/chat.service';
import { ChatSocketService } from '../api/chat-socket.service';
import { AuthService } from '../services/auth.service';
import { AVATAR_PADRAO } from '../core/avatar';


@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
})
export class Chat implements OnInit, OnDestroy {
  private readonly api = inject(ChatService);
  private readonly socket = inject(ChatSocketService);
  private readonly auth = inject(AuthService);

  readonly meuId = this.auth.usuarioLogado()?.id ?? -1;
  readonly foto = AVATAR_PADRAO;

  readonly contatos = signal<ChatContato[]>([]);
  readonly contatoSelecionado = signal<ChatContato | null>(null);
  readonly mensagens = signal<ChatMensagem[]>([]);
  readonly conectado = this.socket.conectado;
  novoTexto = '';

  readonly semContatos = computed(() => this.contatos().length === 0);

  /** Área rolável das bolhas — usada para manter a conversa no fim. */
  private readonly corpo = viewChild<ElementRef<HTMLElement>>('corpoChat');

  private sub?: Subscription;
  /** Ao trocar de conversa o scroll vai para o fim mesmo que o usuário estivesse lendo o histórico. */
  private forcarFim = true;

  constructor() {
    // Sem isto a lista crescia para baixo da dobra e as mensagens novas ficavam
    // invisíveis: o contêiner rolava, mas nunca era rolado.
    effect(() => {
      const lista = this.mensagens();
      const forcar = this.forcarFim;
      // Só consome a flag quando há mensagens: ao trocar de conversa a lista é
      // esvaziada antes do histórico chegar, e esse passo intermediário não pode
      // gastar o "forçar" que pertence à carga seguinte.
      if (lista.length) this.forcarFim = false;
      // Espera o Angular pintar as bolhas novas antes de medir a altura.
      setTimeout(() => this.rolarParaFim(forcar));
    });
  }

  /**
   * Vai para a última mensagem. Se o usuário rolou para cima para ler o histórico,
   * respeita a posição dele e só acompanha quando já estava perto do fim.
   */
  private rolarParaFim(forcar: boolean): void {
    const el = this.corpo()?.nativeElement;
    if (!el) return;
    const distanciaDoFim = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (forcar || distanciaDoFim < 120) {
      el.scrollTop = el.scrollHeight;
    }
  }

  ngOnInit(): void {
    this.socket.conectar();
    this.api.contatos().subscribe({
      next: (lista) => {
        this.contatos.set(lista);
        if (lista.length) this.selecionar(lista[0]);
      },
    });

    this.sub = this.socket.mensagens$.subscribe((msg) => {
      const contato = this.contatoSelecionado();
      if (!contato) return;
      // Só anexa se a mensagem pertence à conversa aberta
      const pertence =
        (msg.de === this.meuId && msg.para === contato.id) ||
        (msg.de === contato.id && msg.para === this.meuId);
      if (pertence) {
        this.mensagens.update((lista) => [...lista, msg]);
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  selecionar(contato: ChatContato): void {
    this.contatoSelecionado.set(contato);
    this.mensagens.set([]);
    this.forcarFim = true;
    this.api.historico(contato.id).subscribe({ next: (h) => this.mensagens.set(h) });
  }

  enviar(): void {
    const texto = this.novoTexto.trim();
    const contato = this.contatoSelecionado();
    if (!texto || !contato) return;
    // O servidor persiste e devolve a mensagem por WebSocket (eco) → é anexada ali
    const ok = this.socket.enviar(contato.id, texto);
    if (ok) {
      this.novoTexto = '';
    }
  }

  minha(msg: ChatMensagem): boolean {
    return msg.de === this.meuId;
  }

  papelLabel(papel: string): string {
    switch (papel) {
      case 'DIRETOR': return 'Diretor';
      case 'PROFESSOR': return 'Professor';
      case 'ALUNO': return 'Aluno';
      default: return papel;
    }
  }
}
