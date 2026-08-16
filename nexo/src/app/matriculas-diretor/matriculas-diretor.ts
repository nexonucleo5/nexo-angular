import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { MatriculasService } from '../api/matriculas.service';
import { TurmasService } from '../api/turmas.service';
import { MatriculaDTO, StatusDocumentacao, StatusMatricula, TurmaDTO } from '../core/api.models';
import { exportarCsv } from '../core/csv.util';

interface MatriculaView {
  id: number;
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
  private readonly turmasApi = inject(TurmasService);

  /** Turmas para o seletor de transferência — carregadas uma vez, junto da lista. */
  readonly turmas = signal<TurmaDTO[]>([]);

  readonly buscaTermo = signal('');
  readonly statusSelecionado = signal('Todos os Status');
  readonly statusOpcoes = ['Todos os Status', 'Ativa', 'Pendente', 'Trancada', 'Cancelada'];

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly matriculas = signal<MatriculaView[]>([]);
  readonly detalhe = signal<MatriculaView | null>(null);

  readonly matriculasFiltradas = computed(() => {
    const termo = this.buscaTermo().toLowerCase();
    const status = this.statusSelecionado();
    return this.matriculas().filter((m) => {
      const buscaOk =
        !termo ||
        m.nome.toLowerCase().includes(termo) ||
        m.matricula.includes(termo);
      const statusOk = status === 'Todos os Status' || STATUS_LABEL[m.status] === status;
      return buscaOk && statusOk;
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
    this.carregar();
    this.turmasApi.listar().subscribe({
      next: (turmas) => this.turmas.set(turmas),
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
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
  }

  // ── Ações da secretaria/diretoria sobre a matrícula ────────────────────────

  readonly acaoEmCurso = signal(false);
  readonly acaoErro = signal<string | null>(null);

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
    chamada.subscribe({
      next: (dto) => {
        const atualizada = this.paraView(dto);
        this.matriculas.update((lista) => lista.map((x) => (x.id === dto.id ? atualizada : x)));
        this.detalhe.set(atualizada);
        this.acaoEmCurso.set(false);
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
    this.api.declaracao(m.id).subscribe({
      next: (pdf) => {
        const url = URL.createObjectURL(pdf);
        const link = document.createElement('a');
        link.href = url;
        link.download = `declaracao-matricula-${m.id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
        this.acaoEmCurso.set(false);
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
