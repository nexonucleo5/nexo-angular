import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { AlunosService } from '../api/alunos.service';
import { MatriculasService } from '../api/matriculas.service';
import { TurmasService } from '../api/turmas.service';
import {
  ChecklistDTO,
  MatriculaDTO,
  ProntuarioDTO,
  ResultadoLoteDTO,
  StatusDocumentacao,
  StatusMatricula,
  TurmaDTO,
} from '../core/api.models';
import { exportarCsv } from '../core/csv.util';

interface MatriculaView {
  id: number;
  alunoId: number;
  nome: string;
  iniciais: string;
  turma: string;
  matricula: string;
  dataMatricula: string;
  status: StatusMatricula;
  documentacao: StatusDocumentacao;
  docPercent: number;
  temAlerta: boolean;
}

/** Abas do modal: o atendimento tem dois momentos distintos. */
type AbaDetalhe = 'matricula' | 'documentos' | 'prontuario';

const STATUS_LABEL: Record<StatusMatricula, string> = {
  ATIVA: 'Ativa',
  PENDENTE: 'Pendente',
  TRANCADA: 'Trancada',
  CANCELADA: 'Cancelada',
};

const DOC_PERCENT: Record<StatusDocumentacao, number> = {
  COMPLETA: 100,
  PENDENTE: 66,
  INCOMPLETA: 33,
};

@Component({
  selector: 'app-matriculas-diretor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './matriculas-diretor.html',
  styleUrl: './matriculas-diretor.scss',
})
export class MatriculasDiretor {
  private readonly api = inject(MatriculasService);
  private readonly alunosApi = inject(AlunosService);
  private readonly turmasApi = inject(TurmasService);
  private readonly route = inject(ActivatedRoute);

  /** Turmas para o seletor de transferência — carregadas uma vez, junto da lista. */
  readonly turmas = signal<TurmaDTO[]>([]);

  readonly buscaTermo = signal('');
  readonly statusSelecionado = signal('Todos os Status');
  readonly statusOpcoes = ['Todos os Status', 'Ativa', 'Pendente', 'Trancada', 'Cancelada'];

  readonly turmaSelecionada = signal('Todas as Turmas');
  /** Opções vindas das próprias linhas: cobre "Sem turma" e só lista o que existe. */
  readonly turmaOpcoes = computed(() => {
    const nomes = new Set(this.matriculas().map((m) => m.turma));
    return ['Todas as Turmas', ...[...nomes].sort()];
  });

  /**
   * Contexto vindo do painel da secretaria (?matricula=ID abre o modal direto,
   * ?turma=ID chega filtrado). Sem isso o clique em "Resolver" na fila jogava a
   * pessoa na lista completa — e ela tinha que reencontrar o aluno na mão.
   */
  private pendenteAbrirId: number | null = null;
  private pendenteTurmaId: number | null = null;

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly matriculas = signal<MatriculaView[]>([]);
  readonly detalhe = signal<MatriculaView | null>(null);

  readonly matriculasFiltradas = computed(() => {
    const termo = this.buscaTermo().toLowerCase();
    const status = this.statusSelecionado();
    const turma = this.turmaSelecionada();
    return this.matriculas().filter((m) => {
      const buscaOk =
        !termo ||
        m.nome.toLowerCase().includes(termo) ||
        m.matricula.includes(termo);
      const statusOk = status === 'Todos os Status' || STATUS_LABEL[m.status] === status;
      const turmaOk = turma === 'Todas as Turmas' || m.turma === turma;
      return buscaOk && statusOk && turmaOk;
    });
  });

  // KPIs derivados da lista real
  readonly totalMatriculas = computed(() => this.matriculas().length);
  readonly docCompleta = computed(() => this.matriculas().filter((m) => m.documentacao === 'COMPLETA').length);
  readonly pendencias = computed(() => this.matriculas().filter((m) => m.documentacao !== 'COMPLETA').length);
  readonly conformidade = computed(() => {
    const total = this.matriculas().length;
    return total ? Math.round((this.docCompleta() / total) * 1000) / 10 : 0;
  });

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    this.pendenteAbrirId = Number(params.get('matricula')) || null;
    this.pendenteTurmaId = Number(params.get('turma')) || null;

    this.carregar();
    this.turmasApi.listar().subscribe({
      next: (turmas) => {
        this.turmas.set(turmas);
        // ?turma= chega como id (é o que a ocupação conhece); o filtro é por nome.
        if (this.pendenteTurmaId != null) {
          const nome = turmas.find((t) => t.id === this.pendenteTurmaId)?.nome;
          if (nome) this.turmaSelecionada.set(nome);
          this.pendenteTurmaId = null;
        }
      },
      error: () => this.turmas.set([]), // sem turmas o seletor de transferência só não aparece
    });
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.api.listar({ size: 100 }).subscribe({
      next: (page) => {
        this.matriculas.set(page.content.map((m) => this.paraView(m)));
        this.carregando.set(false);
        if (this.pendenteAbrirId != null) {
          const alvo = this.matriculas().find((m) => m.id === this.pendenteAbrirId);
          this.pendenteAbrirId = null;
          if (alvo) this.verDetalhes(alvo);
        }
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  private paraView(m: MatriculaDTO): MatriculaView {
    return {
      id: m.id,
      alunoId: m.alunoId,
      nome: m.aluno,
      iniciais: this.iniciais(m.aluno),
      turma: m.turma ?? 'Sem turma',
      matricula: `2024${String(m.id).padStart(3, '0')}`,
      dataMatricula: this.formatarData(m.dataMatricula),
      status: m.status,
      documentacao: m.documentacao,
      docPercent: DOC_PERCENT[m.documentacao],
      temAlerta: m.documentacao === 'INCOMPLETA' || m.status === 'PENDENTE',
    };
  }

  statusLabel(s: StatusMatricula): string {
    return STATUS_LABEL[s];
  }

  verDetalhes(m: MatriculaView): void {
    this.detalhe.set(m);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    this.aba.set('matricula');
    this.checklist.set(null);
    this.prontuario.set(null);
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
  }

  // ── Abas do modal ─────────────────────────────────────────────────────────

  readonly aba = signal<AbaDetalhe>('matricula');

  /** Cada aba carrega o que precisa só quando é aberta — e uma vez só. */
  abrirAba(aba: AbaDetalhe): void {
    this.aba.set(aba);
    const m = this.detalhe();
    if (!m) return;
    if (aba === 'documentos' && !this.checklist()) this.carregarChecklist(m.id);
    if (aba === 'prontuario' && !this.prontuario()) this.carregarProntuario(m.alunoId);
  }

  // ── Checklist de documentos ───────────────────────────────────────────────

  readonly checklist = signal<ChecklistDTO | null>(null);
  readonly carregandoChecklist = signal(false);
  readonly documentoEmCurso = signal<string | null>(null);

  private carregarChecklist(matriculaId: number): void {
    this.carregandoChecklist.set(true);
    this.api.checklist(matriculaId).subscribe({
      next: (c) => {
        this.checklist.set(c);
        this.carregandoChecklist.set(false);
      },
      error: () => {
        this.carregandoChecklist.set(false);
        this.acaoErro.set('Não foi possível carregar o checklist de documentos.');
      },
    });
  }

  /**
   * Marca ou desmarca a entrega. A resposta traz o checklist inteiro já com a
   * situação recalculada, então a linha da lista é atualizada junto — sem isso o
   * cartão continuaria mostrando a documentação antiga até recarregar a página.
   */
  alternarDocumento(item: { tipo: string; entregue: boolean }): void {
    const m = this.detalhe();
    if (!m) return;

    this.documentoEmCurso.set(item.tipo);
    this.acaoErro.set(null);
    const chamada = item.entregue
      ? this.api.removerDocumento(m.id, item.tipo)
      : this.api.registrarDocumento(m.id, item.tipo);

    chamada.subscribe({
      next: (c) => {
        this.checklist.set(c);
        this.documentoEmCurso.set(null);
        this.acaoSucesso.set(item.entregue ? 'Documento removido.' : 'Documento registrado.');
        this.atualizarDocumentacaoNaLista(m.id, c.situacao);
      },
      error: () => {
        this.documentoEmCurso.set(null);
        this.acaoErro.set('Não foi possível atualizar o documento.');
      },
    });
  }

  private atualizarDocumentacaoNaLista(matriculaId: number, situacao: StatusDocumentacao): void {
    const aplicar = (x: MatriculaView): MatriculaView =>
      x.id !== matriculaId
        ? x
        : {
            ...x,
            documentacao: situacao,
            docPercent: DOC_PERCENT[situacao],
            temAlerta: situacao === 'INCOMPLETA' || x.status === 'PENDENTE',
          };
    this.matriculas.update((lista) => lista.map(aplicar));
    const atual = this.detalhe();
    if (atual) this.detalhe.set(aplicar(atual));
  }

  // ── Prontuário ────────────────────────────────────────────────────────────

  readonly prontuario = signal<ProntuarioDTO | null>(null);
  readonly carregandoProntuario = signal(false);

  private carregarProntuario(alunoId: number): void {
    this.carregandoProntuario.set(true);
    this.alunosApi.prontuario(alunoId).subscribe({
      next: (p) => {
        this.prontuario.set(p);
        this.carregandoProntuario.set(false);
      },
      error: () => {
        this.carregandoProntuario.set(false);
        this.acaoErro.set('Não foi possível carregar o prontuário.');
      },
    });
  }

  // ── Rematrícula ───────────────────────────────────────────────────────────

  readonly resultadoLote = signal<ResultadoLoteDTO | null>(null);

  /** Renova o vínculo deste aluno e recarrega a lista, que ganhou uma linha. */
  rematricular(m: MatriculaView): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    this.api.rematricular(m.id).subscribe({
      next: (nova) => {
        this.acaoEmCurso.set(false);
        this.acaoSucesso.set(
          `Rematriculado para ${nova.anoLetivo}: ${nova.turmaAnterior} → ${nova.turmaNova}.`,
        );
        this.carregar();
      },
      error: (erro) => {
        this.acaoEmCurso.set(false);
        this.acaoErro.set(
          erro?.error?.fields?.matricula ??
            erro?.error?.fields?.turma ??
            erro?.error?.message ??
            'Não foi possível rematricular.',
        );
      },
    });
  }

  /**
   * Renova a turma inteira. O resultado fica na tela com quem ficou de fora e o
   * motivo — "12 de 30 renovadas" sem os motivos obrigaria a conferir 18 na mão.
   */
  rematricularTurma(turmaId: number): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    this.resultadoLote.set(null);
    this.api.rematricularTurma(turmaId).subscribe({
      next: (resultado) => {
        this.acaoEmCurso.set(false);
        this.resultadoLote.set(resultado);
        this.carregar();
      },
      error: (erro) => {
        this.acaoEmCurso.set(false);
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível renovar a turma.');
      },
    });
  }

  fecharResultadoLote(): void {
    this.resultadoLote.set(null);
  }

  /** Id da turma atualmente filtrada — o lote age sobre ela. */
  readonly turmaFiltradaId = computed(() => {
    const nome = this.turmaSelecionada();
    if (nome === 'Todas as Turmas') return null;
    return this.turmas().find((t) => t.nome === nome)?.id ?? null;
  });

  emitirHistorico(m: MatriculaView): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    this.alunosApi.historicoEscolar(m.alunoId).subscribe({
      next: (pdf) => {
        this.baixar(pdf, `historico-escolar-${m.alunoId}.pdf`);
        this.acaoEmCurso.set(false);
        this.acaoSucesso.set('Histórico escolar baixado.');
      },
      error: () => {
        this.acaoEmCurso.set(false);
        this.acaoErro.set('Não foi possível emitir o histórico escolar.');
      },
    });
  }

  private baixar(blob: Blob, nome: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
    URL.revokeObjectURL(url);
  }

  // ── Ações da secretaria/diretoria sobre a matrícula ────────────────────────

  readonly acaoEmCurso = signal(false);
  readonly acaoErro = signal<string | null>(null);
  /** Confirmação visível: sem ela, só o erro falava — salvar em silêncio parece travado. */
  readonly acaoSucesso = signal<string | null>(null);

  /** Transições que o backend aceita a partir de cada status (espelho do controller). */
  private static readonly PROXIMOS_STATUS: Record<StatusMatricula, StatusMatricula[]> = {
    PENDENTE: ['ATIVA', 'CANCELADA'],
    ATIVA: ['TRANCADA', 'CANCELADA'],
    TRANCADA: ['ATIVA', 'CANCELADA'],
    CANCELADA: [],
  };

  proximosStatus(m: MatriculaView): StatusMatricula[] {
    return MatriculasDiretor.PROXIMOS_STATUS[m.status];
  }

  readonly docOpcoes: StatusDocumentacao[] = ['COMPLETA', 'PENDENTE', 'INCOMPLETA'];

  mudarStatus(m: MatriculaView, status: StatusMatricula): void {
    this.executar(this.api.atualizarStatus(m.id, status));
  }

  mudarDocumentacao(m: MatriculaView, documentacao: StatusDocumentacao): void {
    this.executar(this.api.atualizarDocumentos(m.id, documentacao));
  }

  transferirTurma(m: MatriculaView, turmaId: string): void {
    const id = Number(turmaId);
    if (!id) return;
    this.executar(this.api.transferirTurma(m.id, id));
  }

  /** Aplica a resposta do servidor na lista e no modal — sem recarregar a página toda. */
  private executar(chamada: Observable<MatriculaDTO>): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    chamada.subscribe({
      next: (dto) => {
        const atualizada = this.paraView(dto);
        this.matriculas.update((lista) => lista.map((x) => (x.id === dto.id ? atualizada : x)));
        this.detalhe.set(atualizada);
        this.acaoEmCurso.set(false);
        this.acaoSucesso.set('Alterações salvas.');
      },
      error: (erro) => {
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível concluir a ação.');
        this.acaoEmCurso.set(false);
      },
    });
  }

  emitirDeclaracao(m: MatriculaView): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    this.api.declaracao(m.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        const link = document.createElement('a');
        link.href = url;
        link.download = `declaracao-matricula-${m.id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
        this.acaoEmCurso.set(false);
        this.acaoSucesso.set('Declaração baixada — confira a pasta de downloads.');
      },
      error: () => {
        this.acaoErro.set('A declaração só pode ser emitida para matrícula ativa.');
        this.acaoEmCurso.set(false);
      },
    });
  }

  private iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[partes.length - 1]?.[0] ?? '')).toUpperCase();
  }

  private formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR');
  }

  /** Exporta a lista filtrada de matrículas para CSV. */
  exportar(): void {
    const linhas = this.matriculasFiltradas().map((m) => [
      m.nome, m.matricula, m.turma, this.statusLabel(m.status), m.documentacao, m.dataMatricula,
    ]);
    exportarCsv('matriculas', ['Nome', 'Matrícula', 'Turma', 'Status', 'Documentação', 'Data'], linhas);
  }
}
