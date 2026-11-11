import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import {
  CHAT_MAX_ANEXOS,
  CHAT_TAMANHO_MAXIMO_ANEXO,
  ChatAnexo,
  ChatContato,
  ChatMensagem,
  ChatService,
} from '../api/chat.service';
import { ChatSocketService } from '../api/chat-socket.service';
import { AuthService } from '../services/auth.service';
import { resolverFoto } from '../core/avatar';
import { ChatAudio } from './chat-audio';
import { ApiErro } from '../core/api.models';

/** Arquivo escolhido (ou áudio gravado) aguardando o envio. */
interface AnexoPendente {
  blob: Blob;
  nome: string;
  tipo: string;
  tamanho: number;
}

/** Duração máxima de um áudio — a 5 MB por anexo sobra folga com Opus. */
const MAX_SEGUNDOS_AUDIO = 5 * 60;

@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatAudio],
  templateUrl: './chat.html',
  styleUrl: './chat.scss',
  host: { '(document:keydown.escape)': 'fecharApagar()' },
})
export class Chat implements OnInit, OnDestroy {
  private readonly api = inject(ChatService);
  private readonly socket = inject(ChatSocketService);
  private readonly auth = inject(AuthService);
  private readonly rota = inject(ActivatedRoute);

  readonly meuId = this.auth.usuarioLogado()?.id ?? -1;
  readonly maxAnexos = CHAT_MAX_ANEXOS;

  readonly contatos = signal<ChatContato[]>([]);
  readonly contatoSelecionado = signal<ChatContato | null>(null);
  readonly mensagens = signal<ChatMensagem[]>([]);
  readonly conectado = this.socket.conectado;
  novoTexto = '';

  readonly semContatos = computed(() => this.contatos().length === 0);

  /** Anexos escolhidos para a próxima mensagem (até 3). */
  readonly pendentes = signal<AnexoPendente[]>([]);
  readonly enviando = signal(false);
  readonly erro = signal<string | null>(null);

  /** Gravação de áudio. */
  readonly gravando = signal(false);
  readonly segundosGravados = signal(0);
  readonly podeGravar = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
    && typeof MediaRecorder !== 'undefined';
  private gravador: MediaRecorder | null = null;
  private trechosAudio: Blob[] = [];
  private cronometro: ReturnType<typeof setInterval> | null = null;
  /** Ao parar, envia direto (true) ou descarta (false). */
  private enviarAoParar = true;

  /** URLs blob: dos anexos exibidos inline (imagem/áudio), por id do anexo. */
  readonly urlsAnexo = signal<Record<string, string>>({});
  private carregando = new Set<string>();

  /** Área rolável das bolhas — usada para manter a conversa no fim. */
  private readonly corpo = viewChild<ElementRef<HTMLElement>>('corpoChat');
  private readonly seletorArquivo = viewChild<ElementRef<HTMLInputElement>>('seletorArquivo');

  private subs: Subscription[] = [];
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
      // Imagens e áudios precisam dos bytes (com token) para virar blob: exibível.
      // untracked: carregarAnexo lê urlsAnexo(), que não deve reexecutar este effect.
      untracked(() => {
        for (const m of lista) for (const a of m.anexos ?? []) if (this.exibivel(a)) this.carregarAnexo(a);
      });
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
        if (!lista.length) return;
        // ?com=<id> abre direto a conversa com aquela pessoa — é assim que o
        // botão de chat do Monitoramento Docente chega aqui. Se o id não estiver
        // entre os contatos (papel que não pode conversar, ou conta removida),
        // cai no primeiro da lista em vez de abrir a tela sem conversa nenhuma.
        const pedido = Number(this.rota.snapshot.queryParamMap.get('com'));
        const alvo = pedido ? lista.find((c) => c.id === pedido) : undefined;
        this.selecionar(alvo ?? lista[0]);
      },
    });

    this.subs.push(this.socket.mensagens$.subscribe((msg) => {
      if (!this.daConversaAberta(msg.de, msg.para)) return;
      // A mensagem com anexo chega pela resposta do POST e pelo eco do socket: sem duplicar.
      this.mensagens.update((lista) =>
        msg.id != null && lista.some((m) => m.id === msg.id) ? lista : [...lista, msg]);
    }));

    this.subs.push(this.socket.apagadas$.subscribe((ev) => {
      if (!this.daConversaAberta(ev.de, ev.para)) return;
      this.removerDaLista(ev.id);
    }));
  }

  ngOnDestroy(): void {
    this.subs.forEach((s) => s.unsubscribe());
    this.cancelarGravacao();
    for (const url of Object.values(this.urlsAnexo())) URL.revokeObjectURL(url);
  }

  private daConversaAberta(de: number, para: number): boolean {
    const contato = this.contatoSelecionado();
    if (!contato) return false;
    return (de === this.meuId && para === contato.id) || (de === contato.id && para === this.meuId);
  }

  selecionar(contato: ChatContato): void {
    this.contatoSelecionado.set(contato);
    this.mensagens.set([]);
    this.pendentes.set([]);
    this.erro.set(null);
    this.forcarFim = true;
    this.api.historico(contato.id).subscribe({ next: (h) => this.mensagens.set(h) });
  }

  enviar(): void {
    const texto = this.novoTexto.trim();
    const contato = this.contatoSelecionado();
    if (!contato || this.enviando()) return;

    const anexos = this.pendentes();
    if (anexos.length) {
      this.enviarComAnexos(contato.id, texto, anexos);
      return;
    }
    if (!texto) return;
    // O servidor persiste e devolve a mensagem por WebSocket (eco) → é anexada ali
    const ok = this.socket.enviar(contato.id, texto);
    if (ok) {
      this.novoTexto = '';
    }
  }

  private enviarComAnexos(para: number, texto: string, anexos: AnexoPendente[]): void {
    this.enviando.set(true);
    this.erro.set(null);
    this.api.enviarComAnexos(para, texto, anexos).subscribe({
      next: (msg) => {
        this.enviando.set(false);
        if (texto === this.novoTexto.trim()) this.novoTexto = '';
        this.pendentes.set([]);
        // Se o socket estiver fora, a resposta do POST garante que a mensagem aparece.
        if (this.daConversaAberta(msg.de, msg.para)) {
          this.mensagens.update((lista) => lista.some((m) => m.id === msg.id) ? lista : [...lista, msg]);
        }
      },
      error: (e: ApiErro) => {
        this.enviando.set(false);
        this.erro.set(e?.message ?? 'Não foi possível enviar a mensagem.');
      },
    });
  }

  // ── Anexos ────────────────────────────────────────────────────────────────

  abrirSeletor(): void {
    this.seletorArquivo()?.nativeElement.click();
  }

  arquivosEscolhidos(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const escolhidos = Array.from(input.files ?? []);
    input.value = ''; // permite escolher o mesmo arquivo de novo depois de remover
    this.adicionarPendentes(escolhidos.map((f) => ({ blob: f, nome: f.name, tipo: f.type, tamanho: f.size })));
  }

  private adicionarPendentes(novos: AnexoPendente[]): void {
    this.erro.set(null);
    const grandes = novos.filter((a) => a.tamanho > CHAT_TAMANHO_MAXIMO_ANEXO);
    const aceitos = novos.filter((a) => a.tamanho <= CHAT_TAMANHO_MAXIMO_ANEXO);
    const vagas = CHAT_MAX_ANEXOS - this.pendentes().length;

    if (grandes.length) this.erro.set('Cada arquivo deve ter no máximo 5 MB.');
    if (aceitos.length > vagas) this.erro.set(`Você pode anexar no máximo ${CHAT_MAX_ANEXOS} arquivos por mensagem.`);
    this.pendentes.update((lista) => [...lista, ...aceitos.slice(0, Math.max(vagas, 0))]);
  }

  removerPendente(indice: number): void {
    this.pendentes.update((lista) => lista.filter((_, i) => i !== indice));
    this.erro.set(null);
  }

  /** Imagem e áudio aparecem dentro da bolha; o resto vira um link de download. */
  exibivel(a: ChatAnexo): boolean {
    return this.ehImagem(a) || this.ehAudio(a);
  }

  ehImagem(a: { tipo: string }): boolean {
    return ['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(a.tipo);
  }

  ehAudio(a: { tipo: string }): boolean {
    return a.tipo.startsWith('audio/');
  }

  private carregarAnexo(a: ChatAnexo): void {
    if (this.urlsAnexo()[a.id] || this.carregando.has(a.id)) return;
    this.carregando.add(a.id);
    this.api.baixarAnexo(a.id).subscribe({
      next: (blob) => {
        this.carregando.delete(a.id);
        // O servidor manda octet-stream para tipos não exibíveis; aqui o tipo é conhecido.
        const url = URL.createObjectURL(new Blob([blob], { type: a.tipo }));
        this.urlsAnexo.update((m) => ({ ...m, [a.id]: url }));
      },
      error: () => this.carregando.delete(a.id),
    });
  }

  baixar(a: ChatAnexo): void {
    const pronto = this.urlsAnexo()[a.id];
    if (pronto) {
      this.dispararDownload(pronto, a.nome);
      return;
    }
    this.api.baixarAnexo(a.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        this.dispararDownload(url, a.nome);
        setTimeout(() => URL.revokeObjectURL(url), 10_000);
      },
      error: () => this.erro.set('Não foi possível baixar o arquivo.'),
    });
  }

  private dispararDownload(url: string, nome: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
  }

  /** "PDF", "DOCX"… — rótulo curto mostrado no cartão do arquivo. */
  extensao(nome: string): string {
    const i = nome.lastIndexOf('.');
    return i > 0 && i < nome.length - 1 ? nome.slice(i + 1, i + 6).toUpperCase() : 'ARQ';
  }

  tamanhoLegivel(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  // ── Áudio ─────────────────────────────────────────────────────────────────

  async iniciarGravacao(): Promise<void> {
    if (this.gravando() || !this.podeGravar) return;
    if (this.pendentes().length >= CHAT_MAX_ANEXOS) {
      this.erro.set(`Você pode anexar no máximo ${CHAT_MAX_ANEXOS} arquivos por mensagem.`);
      return;
    }
    this.erro.set(null);

    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      this.erro.set('Não foi possível acessar o microfone. Verifique a permissão do navegador.');
      return;
    }

    // Chrome/Firefox gravam webm/ogg com Opus; o Safari só mp4.
    const tipo = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4']
      .find((t) => MediaRecorder.isTypeSupported(t));
    const gravador = new MediaRecorder(stream, tipo ? { mimeType: tipo } : undefined);
    this.gravador = gravador;
    this.trechosAudio = [];
    this.enviarAoParar = true;

    gravador.ondataavailable = (e) => { if (e.data.size) this.trechosAudio.push(e.data); };
    gravador.onstop = () => {
      stream.getTracks().forEach((t) => t.stop());
      this.pararCronometro();
      this.gravando.set(false);
      this.gravador = null;
      if (!this.enviarAoParar || !this.trechosAudio.length) return;

      const mime = (gravador.mimeType || tipo || 'audio/webm').split(';')[0];
      const blob = new Blob(this.trechosAudio, { type: mime });
      const ext = mime.includes('mp4') ? 'm4a' : mime.includes('ogg') ? 'ogg' : 'webm';
      const agora = new Date();
      const nome = `audio-${agora.toISOString().slice(0, 19).replace(/[:T]/g, '-')}.${ext}`;
      const contato = this.contatoSelecionado();
      if (!contato) return;
      // Áudio sai na hora, junto com o texto e os arquivos que já estavam esperando.
      this.enviarComAnexos(contato.id, this.novoTexto.trim(),
        [...this.pendentes(), { blob, nome, tipo: mime, tamanho: blob.size }]);
    };

    gravador.start();
    this.gravando.set(true);
    this.segundosGravados.set(0);
    this.cronometro = setInterval(() => {
      this.segundosGravados.update((s) => s + 1);
      if (this.segundosGravados() >= MAX_SEGUNDOS_AUDIO) this.pararGravacao();
    }, 1000);
  }

  /** Para e envia o áudio. */
  pararGravacao(): void {
    if (this.gravador?.state === 'recording') this.gravador.stop();
  }

  /** Para e descarta o áudio. */
  cancelarGravacao(): void {
    this.enviarAoParar = false;
    if (this.gravador?.state === 'recording') this.gravador.stop();
  }

  private pararCronometro(): void {
    if (this.cronometro) clearInterval(this.cronometro);
    this.cronometro = null;
  }

  tempoGravado(): string {
    const s = this.segundosGravados();
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }

  // ── Apagar ────────────────────────────────────────────────────────────────

  /** Mensagem cuja janela "Apagar mensagem?" está aberta. */
  readonly aApagar = signal<ChatMensagem | null>(null);

  abrirApagar(msg: ChatMensagem): void {
    if (msg.id == null) return;
    this.aApagar.set(msg);
  }

  fecharApagar(): void {
    this.aApagar.set(null);
  }

  /** Para todos: só o autor; some da tela dos dois. */
  apagarParaTodos(): void {
    const msg = this.aApagar();
    if (msg?.id == null || !this.minha(msg)) return;
    const id = msg.id;
    this.aApagar.set(null);
    this.api.apagar(id).subscribe({
      // O socket também avisa; remover aqui cobre o caso de ele estar desconectado.
      next: () => this.removerDaLista(id),
      error: (e: ApiErro) => this.erro.set(e?.message ?? 'Não foi possível apagar a mensagem.'),
    });
  }

  /** Para mim: vale para qualquer mensagem da conversa; o outro continua vendo. */
  apagarParaMim(): void {
    const msg = this.aApagar();
    if (msg?.id == null) return;
    const id = msg.id;
    this.aApagar.set(null);
    this.api.ocultar(id).subscribe({
      next: () => this.removerDaLista(id),
      error: (e: ApiErro) => this.erro.set(e?.message ?? 'Não foi possível apagar a mensagem.'),
    });
  }

  private removerDaLista(id: number): void {
    const alvo = this.mensagens().find((m) => m.id === id);
    if (!alvo) return;
    for (const a of alvo.anexos ?? []) {
      const url = this.urlsAnexo()[a.id];
      if (url) URL.revokeObjectURL(url);
    }
    this.mensagens.update((lista) => lista.filter((m) => m.id !== id));
  }

  minha(msg: ChatMensagem): boolean {
    return msg.de === this.meuId;
  }

  fotoDe(contato: ChatContato): string {
    return resolverFoto(contato.foto);
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
