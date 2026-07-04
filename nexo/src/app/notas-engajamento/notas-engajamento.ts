import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface KpiCard {
  label: string;
  valor: string;
  tendencia: string;
  tendenciaClasse: string;
  icone: string;
  iconeBgClasse: string;
  iconeCorClasse: string;
}

export interface BarraDistribuicao {
  faixa: string;
  altura: number;
  corClasse: string;
}

export interface NivelEngajamento {
  label: string;
  qtd: number;
  corClasse: string;
  textClasse: string;
}

export interface CriterioEngajamento {
  titulo: string;
  descricao: string;
  nivel: string;
  nivelTextClasse: string;
  nivelBgClasse: string;
}

/**
 * AlunoNota — notas como strings para binding com input[(ngModel)].
 * Suporta vírgula como separador decimal ("8,5").
 * média, status e cor são CALCULADOS em tempo real pelos métodos do componente.
 */
export interface AlunoNota {
  matricula: string;
  nome: string;
  foto: string;
  p1: string;
  p2: string;
  t1: string;
  part: string;
  frequencia: number;
  engajamento: number;
}

// ── Componente ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-notas-engajamento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notas-engajamento.html',
  styleUrl: './notas-engajamento.scss',
})
export class NotasEngajamento {

  // ── Filtros ────────────────────────────────────────────────────────────────
  turmaSelecionada      = '2º Ano A';
  disciplinaSelecionada = 'Matemática';
  periodoSelecionado    = 'Este Bimestre';
  statusSelecionado     = 'Todos';
  mensagemSucesso       = '';

  turmas       = ['2º Ano A', '2º Ano B', '3º Ano A'];
  disciplinas  = ['Matemática', 'Português', 'História'];
  periodos     = ['Este Bimestre', 'Último Bimestre', 'Ano Letivo'];
  statusOpcoes = ['Todos', 'Aprovados', 'Em Risco'];

  // ── KPI Cards ──────────────────────────────────────────────────────────────
  kpis: KpiCard[] = [
    { label: 'Média Geral da Turma', valor: '7.8',  tendencia: '+0.4', tendenciaClasse: 'text-green bg-green-dim', icone: 'bi-geo',               iconeBgClasse: 'bg-green-dim', iconeCorClasse: 'text-green' },
    { label: 'Taxa de Aprovação',    valor: '85%',  tendencia: '+3%',  tendenciaClasse: 'text-green bg-green-dim', icone: 'bi-check-lg',          iconeBgClasse: 'bg-green',     iconeCorClasse: 'text-white' },
    { label: 'Engajamento Médio',    valor: '82%',  tendencia: '+5%',  tendenciaClasse: 'text-green bg-green-dim', icone: 'bi-activity',          iconeBgClasse: 'bg-blue',      iconeCorClasse: 'text-white' },
    { label: 'Alunos em Risco',      valor: '4',    tendencia: '-2',   tendenciaClasse: 'text-blue bg-blue-dim',  icone: 'bi-exclamation-triangle', iconeBgClasse: 'bg-amber',  iconeCorClasse: 'text-white' },
  ];

  // ── Gráfico de barras ──────────────────────────────────────────────────────
  distribuicaoNotas: BarraDistribuicao[] = [
    { faixa: '0–4',  altura: 12.5,  corClasse: 'bg-danger' },
    { faixa: '4–6',  altura: 31.25, corClasse: 'bg-amber'  },
    { faixa: '6–8',  altura: 75,    corClasse: 'bg-blue'   },
    { faixa: '8–10', altura: 81.25, corClasse: 'bg-green'  },
  ];

  // ── Engajamento ────────────────────────────────────────────────────────────
  niveisEngajamento: NivelEngajamento[] = [
    { label: 'Alto',  qtd: 18, corClasse: 'bg-green', textClasse: 'text-green' },
    { label: 'Médio', qtd: 10, corClasse: 'bg-blue',  textClasse: 'text-blue'  },
    { label: 'Baixo', qtd: 4,  corClasse: 'bg-amber', textClasse: 'text-amber' },
  ];

  criterios: CriterioEngajamento[] = [
    { titulo: 'Tempo de Acesso Semanal',    descricao: '12h 30min média',  nivel: 'Alto',  nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Participação em Atividades', descricao: '82% de conclusão', nivel: 'Alto',  nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Visualização de Materiais',  descricao: '95% dos materiais', nivel: 'Alto', nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Interação em Fóruns',        descricao: '68% participam',   nivel: 'Médio', nivelTextClasse: 'text-amber', nivelBgClasse: 'bg-amber-dim' },
  ];

  // ── Alunos (sem media estática — calculada em tempo real) ─────────────────
  alunos: AlunoNota[] = [
    { matricula: '2024001', nome: 'Ana Carolina Silva',   foto: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150', p1: '8,5', p2: '9',   t1: '8',   part: '9,5', frequencia: 95, engajamento: 92 },
    { matricula: '2024002', nome: 'Bruno Henrique Costa', foto: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150', p1: '7',   p2: '7,5', t1: '8,5', part: '7',   frequencia: 88, engajamento: 75 },
    { matricula: '2024003', nome: 'Camila Rodrigues',     foto: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150', p1: '5,5', p2: '6',   t1: '6,5', part: '5',   frequencia: 72, engajamento: 58 },
    { matricula: '2024004', nome: 'Daniel Santos',        foto: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150', p1: '9',   p2: '8,5', t1: '9,5', part: '10',  frequencia: 98, engajamento: 95 },
  ];

  // ── Filtro de alunos ───────────────────────────────────────────────────────
  get alunosFiltrados(): AlunoNota[] {
    if (this.statusSelecionado === 'Aprovados') return this.alunos.filter(a => this.calcularMedia(a) >= 6);
    if (this.statusSelecionado === 'Em Risco')  return this.alunos.filter(a => this.calcularMedia(a) < 6);
    return this.alunos;
  }

  // ── ITEM 2: Cálculo dinâmico de média ─────────────────────────────────────

  /**
   * Converte string com vírgula para número.
   * Retorna 0 para entradas inválidas ou vazias.
   */
  private parseNota(valor: string): number {
    const num = parseFloat((valor ?? '').toString().replace(',', '.'));
    return isNaN(num) ? 0 : Math.max(0, Math.min(10, num));
  }

  /**
   * Calcula a média do aluno em tempo real a partir das notas editadas.
   * É chamado pelo template a cada mudança nos inputs via ngModel.
   */
  calcularMedia(aluno: AlunoNota): number {
    const notas = [aluno.p1, aluno.p2, aluno.t1, aluno.part].map(n => this.parseNota(n));
    const soma  = notas.reduce((acc, n) => acc + n, 0);
    return parseFloat((soma / notas.length).toFixed(2));
  }

  /** Cor da média baseada no valor calculado */
  getMediaClasse(media: number): string {
    if (media >= 7) return 'text-green';
    if (media >= 5) return 'text-blue';
    return 'text-danger';
  }

  /** Label de status calculado dinamicamente */
  getStatus(aluno: AlunoNota): string {
    const m = this.calcularMedia(aluno);
    if (m >= 8.5) return 'Excelente';
    if (m >= 7)   return 'Bom';
    if (m >= 5)   return 'Regular';
    return 'Atenção';
  }

  /** Classes CSS do badge de status calculado dinamicamente */
  getStatusClasses(aluno: AlunoNota): string[] {
    const m = this.calcularMedia(aluno);
    if (m >= 8.5) return ['text-green', 'bg-green-dim'];
    if (m >= 7)   return ['text-blue',  'bg-blue-dim' ];
    if (m >= 5)   return ['text-amber', 'bg-amber-dim'];
    return ['text-danger', 'bg-danger-dim'];
  }

  // ── Salvar ─────────────────────────────────────────────────────────────────

  salvarAlteracoes(): void {
    // Inclui a média calculada no payload para o backend
    const payload = this.alunos.map(a => ({
      matricula:       a.matricula,
      nome:            a.nome,
      p1:              this.parseNota(a.p1),
      p2:              this.parseNota(a.p2),
      t1:              this.parseNota(a.t1),
      part:            this.parseNota(a.part),
      mediaCalculada:  this.calcularMedia(a),   // ← calculado, não estático
      frequencia:      a.frequencia,
      engajamento:     a.engajamento,
    }));

    // TODO: substituir por chamada HTTP ao backend
    console.log('Payload para o backend:', payload);

    this.mensagemSucesso = '✅ Alterações salvas com sucesso!';
    setTimeout(() => (this.mensagemSucesso = ''), 3000);
  }
}