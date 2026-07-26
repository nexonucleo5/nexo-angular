import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatriculasService } from '../api/matriculas.service';
import { MatriculaDTO, StatusDocumentacao, StatusMatricula } from '../core/api.models';
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
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
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
