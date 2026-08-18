import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chat } from '../chat/chat';
import { ComunicacaoService } from '../api/comunicacao.service';
import { TurmasService } from '../api/turmas.service';
import { AlunosService } from '../api/alunos.service';
import { AvisoDTO, ConversaDTO, DuvidaDTO, NotaDTO, TurmaDTO } from '../core/api.models';
import { AVATAR_PADRAO } from '../core/avatar';

interface ChatMessage {
  remetente: 'aluno' | 'professor';
  texto: string;
  data: string;
}

interface MensagemView {
  id: number;
  aluno: string;
  assunto: string;
  horario: string;
  prioridade: string;
  avatar: string;
  historico: ChatMessage[];
}

interface AvisoView {
  titulo: string;
  conteudo: string;
  horario: string;
  destino: string;
}

interface DuvidaView {
  id: number;
  aluno: string;
  tempo: string;
  status: 'Pendente' | 'Respondida';
  pergunta: string;
  materia: string;
  avatar: string;
}

interface AlunoLista {
  alunoId: number;
  nome: string;
  turma: string;
}

interface NotaView {
  disciplina: string;
  valor: string;
}

interface ObservacaoView {
  professor: string;
  data: string;
  texto: string;
}


@Component({
  selector: 'app-comunicacao',
  standalone: true,
  imports: [CommonModule, FormsModule, Chat],
  templateUrl: './comunicacao.html',
  styleUrl: './comunicacao.scss',
})
export class Comunicacao implements OnInit {
  private readonly comunicacao = inject(ComunicacaoService);
  private readonly turmasApi = inject(TurmasService);
  private readonly alunosApi = inject(AlunosService);

  /** Avatar padrão para os templates (a lista de alunos ainda não traz foto). */
  readonly avatarPadrao = AVATAR_PADRAO;

  abaAtiva = 'mensagens';
  buscaMensagem = '';
  buscaAluno = '';
  textoResposta = '';
  novaObservacao = '';

  // publicar aviso
  novoAvisoTitulo = '';
  novoAvisoConteudo = '';
  mostrarFormAviso = false;

  readonly mensagens = signal<MensagemView[]>([]);
  readonly avisos = signal<AvisoView[]>([]);
  readonly duvidas = signal<DuvidaView[]>([]);
  readonly alunos = signal<AlunoLista[]>([]);
  /**
   * A carga da lista falhou. Existe porque a versão anterior engolia o erro: a
   * chamada não tinha ramo de falha, então uma resposta 403 deixava a aba de
   * alunos vazia e indistinguível de uma turma sem aluno nenhum.
   */
  readonly erroAlunos = signal(false);
  private readonly turmas = signal<TurmaDTO[]>([]);

  mensagemSelecionada: MensagemView | null = null;
  alunoSelecionado: AlunoLista | null = null;
  readonly notasAluno = signal<NotaView[]>([]);
  readonly observacoesAluno = signal<ObservacaoView[]>([]);

  turmaSelecionada = '';

  readonly turmasDisponiveis = computed(() =>
    Array.from(new Set(this.alunos().map((a) => a.turma))).sort(),
  );

  readonly stats = computed(() => [
    { label: 'Conversas', value: `${this.mensagens().length}`, icon: 'bi-chat-left-dots', color: 'orange' },
    { label: 'Dúvidas Não Respondidas', value: `${this.duvidas().filter((d) => d.status === 'Pendente').length}`, icon: 'bi-question-circle', color: 'red' },
    { label: 'Avisos Publicados', value: `${this.avisos().length}`, icon: 'bi-megaphone', color: 'green' },
    { label: 'Dúvidas Respondidas', value: `${this.duvidas().filter((d) => d.status === 'Respondida').length}`, icon: 'bi-check-circle', color: 'green' },
  ]);

  readonly mediaGeralAluno = computed(() => {
    const vals = this.notasAluno().map((n) => Number(n.valor)).filter((v) => !isNaN(v) && v > 0);
    if (!vals.length) return '—';
    return (vals.reduce((s, v) => s + v, 0) / vals.length).toFixed(2);
  });

  ngOnInit(): void {
    this.comunicacao.listarConversas().subscribe({
      next: (conversas) => this.mensagens.set(conversas.map((c) => this.conversaView(c))),
    });
    this.comunicacao.listarAvisos().subscribe({
      next: (avisos) => this.avisos.set(avisos.map((a) => this.avisoView(a))),
    });
    this.comunicacao.listarDuvidas().subscribe({
      next: (duvidas) => this.duvidas.set(duvidas.map((d) => this.duvidaView(d))),
    });
    this.turmasApi.listar().subscribe({ next: (t) => this.turmas.set(t) });
    // GET /api/alunos já chega recortado pelas turmas que este professor leciona —
    // antes esta lista vinha de /api/matriculas, que é do diretor, e o professor
    // tomava 403 sem nada na tela dizendo por que a lista estava vazia.
    this.alunosApi.listar().subscribe({
      next: (lista) => {
        this.alunos.set(
          lista.map((a) => ({
            alunoId: a.id,
            nome: a.nome,
            turma: a.turma ?? 'Sem turma',
          })),
        );
        const turmas = this.turmasDisponiveis();
        if (turmas.length) this.turmaSelecionada = turmas[0];
      },
      error: () => this.erroAlunos.set(true),
    });
  }

  // ── Mensagens ─────────────────────────────────────────────────────────
  private conversaView(c: ConversaDTO): MensagemView {
    return {
      id: c.id,
      aluno: c.participante,
      assunto: c.assunto,
      horario: this.formatarRelativo(c.atualizadaEm),
      prioridade: 'medio',
      avatar: AVATAR_PADRAO,
      historico: c.mensagens.map((m) => ({
        remetente: m.minha ? 'professor' : 'aluno',
        texto: m.texto,
        data: this.formatarRelativo(m.criadaEm),
      })),
    };
  }

  filtrarMensagens(): MensagemView[] {
    const termo = this.buscaMensagem.toLowerCase();
    return this.mensagens().filter(
      (m) => m.aluno.toLowerCase().includes(termo) || m.assunto.toLowerCase().includes(termo),
    );
  }

  selecionarMensagem(msg: MensagemView): void {
    this.mensagemSelecionada = msg;
  }

  enviarResposta(): void {
    if (!this.textoResposta.trim() || !this.mensagemSelecionada) return;
    const texto = this.textoResposta.trim();
    const conversaId = this.mensagemSelecionada.id;
    this.textoResposta = '';
    this.comunicacao.responder(conversaId, texto).subscribe({
      next: (msg) => {
        this.mensagemSelecionada?.historico.push({
          remetente: 'professor',
          texto: msg.texto,
          data: this.formatarRelativo(msg.criadaEm),
        });
      },
    });
  }

  // ── Avisos ────────────────────────────────────────────────────────────
  private avisoView(a: AvisoDTO): AvisoView {
    return {
      titulo: a.titulo,
      conteudo: a.conteudo,
      horario: this.formatarRelativo(a.criadoEm),
      destino: a.destino || 'Todas as turmas',
    };
  }

  publicarAviso(): void {
    if (!this.novoAvisoTitulo.trim() || !this.novoAvisoConteudo.trim()) return;
    this.comunicacao
      .publicarAviso({ titulo: this.novoAvisoTitulo.trim(), conteudo: this.novoAvisoConteudo.trim() })
      .subscribe({
        next: (a) => {
          this.avisos.update((lista) => [this.avisoView(a), ...lista]);
          this.novoAvisoTitulo = '';
          this.novoAvisoConteudo = '';
          this.mostrarFormAviso = false;
        },
      });
  }

  // ── Dúvidas ───────────────────────────────────────────────────────────
  private duvidaView(d: DuvidaDTO): DuvidaView {
    return {
      id: d.id,
      aluno: d.aluno,
      tempo: this.formatarRelativo(d.criadaEm),
      status: d.status === 'ABERTA' ? 'Pendente' : 'Respondida',
      pergunta: d.pergunta,
      materia: d.disciplina,
      avatar: AVATAR_PADRAO,
    };
  }

  responderDuvida(duvida: DuvidaView): void {
    if (duvida.status === 'Respondida') return;
    const resposta = window.prompt(`Responder à dúvida de ${duvida.aluno}:`);
    if (!resposta || !resposta.trim()) return;
    this.comunicacao.responderDuvida(duvida.id, resposta.trim()).subscribe({
      next: () =>
        this.duvidas.update((lista) =>
          lista.map((d) => (d.id === duvida.id ? { ...d, status: 'Respondida' as const } : d)),
        ),
    });
  }

  // ── Perfil do aluno ───────────────────────────────────────────────────
  alunosFiltrados(): AlunoLista[] {
    const termo = this.buscaAluno.toLowerCase();
    return this.alunos().filter(
      (a) =>
        a.turma === this.turmaSelecionada &&
        a.nome.toLowerCase().includes(termo),
    );
  }

  selecionarAluno(aluno: AlunoLista): void {
    this.alunoSelecionado = aluno;
    this.notasAluno.set([]);
    this.observacoesAluno.set([]);

    const turma = this.turmas().find((t) => t.nome === aluno.turma);
    if (turma) {
      this.turmasApi.notas(turma.id).subscribe({
        next: (notas) =>
          this.notasAluno.set(
            notas
              .filter((n) => n.alunoId === aluno.alunoId)
              .map((n: NotaDTO) => ({ disciplina: n.disciplina, valor: n.media != null ? n.media.toFixed(1) : '—' })),
          ),
      });
    }

    this.alunosApi.listarObservacoes(aluno.alunoId).subscribe({
      next: (obs) =>
        this.observacoesAluno.set(
          obs.map((o) => ({ professor: o.autor, data: this.formatarData(o.criadaEm), texto: o.texto })),
        ),
    });
  }

  salvarObservacao(): void {
    if (!this.novaObservacao.trim() || !this.alunoSelecionado) return;
    const texto = this.novaObservacao.trim();
    const alunoId = this.alunoSelecionado.alunoId;
    this.novaObservacao = '';
    this.alunosApi.criarObservacao(alunoId, texto).subscribe({
      next: (o) =>
        this.observacoesAluno.update((lista) => [
          { professor: o.autor, data: this.formatarData(o.criadaEm), texto: o.texto },
          ...lista,
        ]),
    });
  }

  mudarAba(aba: string): void {
    this.abaAtiva = aba;
  }

  // ── Helpers ───────────────────────────────────────────────────────────
  private formatarRelativo(iso: string): string {
    if (!iso) return '';
    const ms = Date.now() - new Date(iso).getTime();
    const horas = Math.floor(ms / 3_600_000);
    if (horas < 1) return 'agora há pouco';
    if (horas < 24) return `há ${horas}h`;
    const dias = Math.floor(horas / 24);
    if (dias === 1) return 'ontem';
    if (dias < 30) return `há ${dias} dias`;
    return this.formatarData(iso);
  }

  private formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' });
  }
}
