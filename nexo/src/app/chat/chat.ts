import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ChatContato, ChatMensagem, ChatService } from '../api/chat.service';
import { ChatSocketService } from '../api/chat-socket.service';
import { AuthService } from '../services/auth.service';

const FOTO_PADRAO = 'assets/imagensProjeto/gabrielZapelini.png';

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
  readonly foto = FOTO_PADRAO;

  readonly contatos = signal<ChatContato[]>([]);
  readonly contatoSelecionado = signal<ChatContato | null>(null);
  readonly mensagens = signal<ChatMensagem[]>([]);
  readonly conectado = this.socket.conectado;
  novoTexto = '';

  readonly semContatos = computed(() => this.contatos().length === 0);

  private sub?: Subscription;

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
    return papel === 'DIRETOR' ? 'Diretor' : papel === 'PROFESSOR' ? 'Professor' : papel;
  }
}
