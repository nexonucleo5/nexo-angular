import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AvaliacoesService } from '../api/avaliacoes.service';
import { TurmasService } from '../api/turmas.service';
import { AvaliacaoDTO, QuestaoDTO, StatusAvaliacao, TurmaDTO } from '../core/api.models';

interface AvaliacaoView {
  titulo: string;
  tipo: string;
  turma: string;
  data: string;
  status: string;
  statusClasse: string;
  corrigidas: number;
  total: number;
}

interface FilaView {
  avaliacao: string;
  turma: string;
  data: string;
  pendentes: number;
}

interface QuestaoView {
  id: number;
  enunciado: string;
  tipo: string;
  dificuldade: string;
  tags: string[];
  tipoRaw: 'OBJETIVA' | 'DISSERTATIVA';
  dificuldadeRaw: 'FACIL' | 'MEDIA' | 'DIFICIL';
  disciplinaRaw: string;
}

const STATUS_LABEL: Record<StatusAvaliacao, string> = {
  RASCUNHO: 'Rascunho',
  PUBLICADA: 'Aguardando',
  EM_CORRECAO: 'Em Correção',
  CORRIGIDA: 'Concluída',
};

const STATUS_CLASSE: Record<StatusAvaliacao, string> = {
  RASCUNHO: 'aguardando',
  PUBLICADA: 'aguardando',
  EM_CORRECAO: 'em-correcao',
  CORRIGIDA: 'concluida',
};

@Component({
  selector: 'app-avaliacoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './avaliacoes.html',
  styleUrl: './avaliacoes.scss',
})
export class Avaliacoes {
  private readonly api = inject(AvaliacoesService);
  private readonly turmasApi = inject(TurmasService);

  abaAtiva = 'ativas';
  buscaTermo = '';
  mensagemSucesso = '';
  mensagemErro = '';

  readonly carregando = signal(true);
  private readonly avaliacoes = signal<AvaliacaoView[]>([]);
  readonly detalhe = signal<AvaliacaoView | null>(null);
  readonly filaCorrecao = signal<FilaView[]>([]);
  readonly bancoQuestoes = signal<QuestaoView[]>([]);
  readonly turmas = signal<TurmaDTO[]>([]);

  tiposAvaliacao = ['Prova', 'Trabalho', 'Quiz', 'Exercício'];

  novaAvaliacao = {
    titulo: '',
    tipo: '',
    turmaId: null as number | null,
    notaMaxima: 10,
    peso: 3,
    dataAplicacao: '',
    instrucoes: '',
  };

  // Nova questão / edição (banco de questões)
  mostrarFormQuestao = false;
  editandoQuestaoId: number | null = null;
  novaQuestao = {
    enunciado: '',
    disciplina: '',
    tipo: 'OBJETIVA' as 'OBJETIVA' | 'DISSERTATIVA',
    dificuldade: 'MEDIA' as 'FACIL' | 'MEDIA' | 'DIFICIL',
  };

  readonly stats = computed(() => {
    const lista = this.avaliacoes();
    const ativas = lista.filter((a) => a.status !== 'Concluída').length;
    const pendentes = lista.reduce((s, a) => s + Math.max(0, a.total - a.corrigidas), 0);
    const concluidas = lista.filter((a) => a.status === 'Concluída').length;
    return [
      { label: 'Avaliações Ativas', value: `${ativas}`, icon: 'bi-journal-text', color: 'blue' },
      { label: 'Pendentes de Correção', value: `${pendentes}`, icon: 'bi-clock-history', color: 'orange' },
      { label: 'Concluídas', value: `${concluidas}`, icon: 'bi-check2-circle', color: 'green' },
      { label: 'Total de Avaliações', value: `${lista.length}`, icon: 'bi-graph-up', color: 'blue' },
    ];
  });

  readonly avaliacoesFiltradas = computed(() => {
    const termo = this.buscaTermo.toLowerCase();
    return this.avaliacoes().filter(
      (a) => a.titulo.toLowerCase().includes(termo) || a.tipo.toLowerCase().includes(termo),
    );
  });

  constructor() {
    this.carregarTudo();
    this.turmasApi.listar().subscribe({ next: (t) => this.turmas.set(t), error: () => {} });
  }

  carregarTudo(): void {
    this.carregando.set(true);
    this.api.listar().subscribe({
      next: (lista) => {
        this.avaliacoes.set(lista.map((a) => this.paraView(a)));
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
    this.api.filaCorrecao().subscribe({
      next: (lista) =>
        this.filaCorrecao.set(
          lista.map((a) => ({
            avaliacao: a.titulo,
            turma: a.turma ?? '—',
            data: this.formatarData(a.data),
            pendentes: a.pendentesCorrecao,
          })),
        ),
      error: () => this.filaCorrecao.set([]),
    });
    this.api.listarQuestoes().subscribe({
      next: (lista) => this.bancoQuestoes.set(lista.map((q) => this.questaoView(q))),
      error: () => this.bancoQuestoes.set([]),
    });
  }

  private paraView(a: AvaliacaoDTO): AvaliacaoView {
    return {
      titulo: a.titulo,
      tipo: a.tipo,
      turma: a.turma ?? '—',
      data: this.formatarData(a.data),
      status: STATUS_LABEL[a.status],
      statusClasse: STATUS_CLASSE[a.status],
      corrigidas: Math.max(0, a.entregas - a.pendentesCorrecao),
      total: a.entregas,
    };
  }

  private questaoView(q: QuestaoDTO): QuestaoView {
    const dif = { FACIL: 'Fácil', MEDIA: 'Médio', DIFICIL: 'Difícil' }[q.dificuldade] ?? q.dificuldade;
    return {
      id: q.id,
      enunciado: q.enunciado,
      tipo: q.tipo === 'OBJETIVA' ? 'Objetiva' : 'Discursiva',
      dificuldade: dif,
      tags: q.disciplina ? [q.disciplina] : [],
      tipoRaw: q.tipo,
      dificuldadeRaw: q.dificuldade,
      disciplinaRaw: q.disciplina ?? '',
    };
  }

  setAba(aba: string): void {
    this.abaAtiva = aba;
  }

  verDetalhes(a: AvaliacaoView): void {
    this.detalhe.set(a);
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
  }

  criarAvaliacao(): void {
    if (!this.novaAvaliacao.titulo.trim()) {
      this.mensagemErro = 'O título da avaliação é obrigatório.';
      return;
    }
    if (!this.novaAvaliacao.tipo) {
      this.mensagemErro = 'Selecione o tipo de avaliação.';
      return;
    }
    if (this.novaAvaliacao.turmaId == null) {
      this.mensagemErro = 'Selecione a turma.';
      return;
    }

    this.mensagemErro = '';
    this.api
      .criar({
        titulo: this.novaAvaliacao.titulo.trim(),
        tipo: this.novaAvaliacao.tipo,
        turmaId: this.novaAvaliacao.turmaId,
        data: this.novaAvaliacao.dataAplicacao || undefined,
      })
      .subscribe({
        next: (criada) => {
          this.avaliacoes.update((lista) => [this.paraView(criada), ...lista]);
          this.mensagemSucesso = '✅ Avaliação criada com sucesso!';
          this.novaAvaliacao = { titulo: '', tipo: '', turmaId: null, notaMaxima: 10, peso: 3, dataAplicacao: '', instrucoes: '' };
          setTimeout(() => {
            this.mensagemSucesso = '';
            this.setAba('ativas');
          }, 1500);
        },
        error: () => (this.mensagemErro = 'Falha ao criar a avaliação.'),
      });
  }

  abrirNovaQuestao(): void {
    this.editandoQuestaoId = null;
    this.novaQuestao = { enunciado: '', disciplina: '', tipo: 'OBJETIVA', dificuldade: 'MEDIA' };
    this.mostrarFormQuestao = !this.mostrarFormQuestao;
  }

  editarQuestao(q: QuestaoView): void {
    this.editandoQuestaoId = q.id;
    this.novaQuestao = {
      enunciado: q.enunciado,
      disciplina: q.disciplinaRaw,
      tipo: q.tipoRaw,
      dificuldade: q.dificuldadeRaw,
    };
    this.mostrarFormQuestao = true;
  }

  salvarQuestao(): void {
    if (!this.novaQuestao.enunciado.trim()) {
      this.mensagemErro = 'O enunciado da questão é obrigatório.';
      return;
    }
    this.mensagemErro = '';
    const payload = {
      enunciado: this.novaQuestao.enunciado.trim(),
      disciplina: this.novaQuestao.disciplina.trim() || undefined,
      tipo: this.novaQuestao.tipo,
      dificuldade: this.novaQuestao.dificuldade,
    };
    const id = this.editandoQuestaoId;
    const req = id != null ? this.api.atualizarQuestao(id, payload) : this.api.criarQuestao(payload);
    req.subscribe({
      next: (q) => {
        const view = this.questaoView(q);
        this.bancoQuestoes.update((lista) =>
          id != null ? lista.map((x) => (x.id === id ? view : x)) : [view, ...lista],
        );
        this.novaQuestao = { enunciado: '', disciplina: '', tipo: 'OBJETIVA', dificuldade: 'MEDIA' };
        this.mostrarFormQuestao = false;
        this.editandoQuestaoId = null;
      },
      error: () => (this.mensagemErro = 'Falha ao salvar a questão.'),
    });
  }

  excluirQuestao(q: QuestaoView): void {
    if (!window.confirm(`Excluir a questão "${q.enunciado.slice(0, 40)}…"?`)) return;
    this.api.excluirQuestao(q.id).subscribe({
      next: () => this.bancoQuestoes.update((lista) => lista.filter((x) => x.id !== q.id)),
      error: () => (this.mensagemErro = 'Falha ao excluir a questão.'),
    });
  }

  private formatarData(iso: string): string {
    if (!iso) return '—';
    return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }
}
