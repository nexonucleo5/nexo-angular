import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { forkJoin } from 'rxjs';
import { TurmasService } from '../api/turmas.service';
import { AlunosService } from '../api/alunos.service';
import { NotaDTO, TurmaDTO } from '../core/api.models';
import { AVATAR_PADRAO } from '../core/avatar';

interface AlunoNotaView {
  alunoId: number;
  matricula: string;
  nome: string;
  foto: string;
  disciplina: string;
  periodo: string;
  p1: string;
  p2: string;
  t1: string;
  part: string;
}

interface BarraDistribuicao {
  faixa: string;
  altura: number;
  corClasse: string;
}


@Component({
  selector: 'app-notas-engajamento',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './notas-engajamento.html',
  styleUrl: './notas-engajamento.scss',
})
export class NotasEngajamento implements OnInit {
  private readonly turmasApi = inject(TurmasService);
  private readonly alunosApi = inject(AlunosService);

  readonly turmas = signal<TurmaDTO[]>([]);
  turmaSelecionadaId: number | null = null;
  disciplinaSelecionada = 'História';
  periodoSelecionado = 'Este Bimestre';
  statusSelecionado = 'Todos';

  disciplinas = ['História', 'Matemática', 'Português'];
  periodos = ['Este Bimestre', 'Último Bimestre', 'Ano Letivo'];
  statusOpcoes = ['Todos', 'Aprovados', 'Em Risco'];

  mensagemSucesso = '';
  readonly salvando = signal(false);
  readonly alunos = signal<AlunoNotaView[]>([]);

  ngOnInit(): void {
    this.turmasApi.listar().subscribe({
      next: (turmas) => {
        this.turmas.set(turmas);
        if (turmas.length) {
          this.turmaSelecionadaId = turmas[0].id;
          this.carregarNotas();
        }
      },
    });
  }

  carregarNotas(): void {
    const turmaId = this.turmaSelecionadaId;
    if (turmaId == null) return;
    this.turmasApi.notas(turmaId, this.disciplinaSelecionada).subscribe({
      next: (notas) => this.alunos.set(notas.map((n) => this.paraView(n))),
      error: () => this.alunos.set([]),
    });
  }

  private paraView(n: NotaDTO): AlunoNotaView {
    return {
      alunoId: n.alunoId,
      matricula: `2024${String(n.alunoId).padStart(3, '0')}`,
      nome: n.aluno,
      foto: AVATAR_PADRAO,
      disciplina: n.disciplina,
      periodo: n.periodo,
      p1: this.fmt(n.p1),
      p2: this.fmt(n.p2),
      t1: this.fmt(n.t1),
      part: this.fmt(n.participacao),
    };
  }

  // ── Cálculos de exibição ──────────────────────────────────────────────
  media(a: AlunoNotaView): number {
    const vals = [a.p1, a.p2, a.t1, a.part].map((v) => this.num(v)).filter((v) => v !== null) as number[];
    if (!vals.length) return 0;
    return Math.round((vals.reduce((s, v) => s + v, 0) / vals.length) * 100) / 100;
  }

  mediaClasse(a: AlunoNotaView): string {
    const m = this.media(a);
    if (m >= 8) return 'text-green';
    if (m >= 6) return 'text-blue';
    return 'text-danger';
  }

  status(a: AlunoNotaView): string {
    const m = this.media(a);
    if (m >= 8) return 'Excelente';
    if (m >= 6) return 'Bom';
    return 'Atenção';
  }

  statusClasses(a: AlunoNotaView): string[] {
    const m = this.media(a);
    if (m >= 8) return ['text-green', 'bg-green-dim'];
    if (m >= 6) return ['text-blue', 'bg-blue-dim'];
    return ['text-amber', 'bg-amber-dim'];
  }

  get alunosFiltrados(): AlunoNotaView[] {
    const lista = this.alunos();
    if (this.statusSelecionado === 'Aprovados') return lista.filter((a) => this.media(a) >= 6);
    if (this.statusSelecionado === 'Em Risco') return lista.filter((a) => this.media(a) < 6);
    return lista;
  }

  // ── KPIs derivados ────────────────────────────────────────────────────
  get kpis() {
    const lista = this.alunos();
    const total = lista.length;
    const medias = lista.map((a) => this.media(a)).filter((m) => m > 0);
    const mediaGeral = medias.length ? medias.reduce((s, m) => s + m, 0) / medias.length : 0;
    const aprovados = medias.filter((m) => m >= 6).length;
    const taxaAprovacao = medias.length ? Math.round((aprovados / medias.length) * 100) : 0;
    const emRisco = lista.filter((a) => this.media(a) < 6).length;
    return [
      { label: 'Média Geral da Turma', valor: mediaGeral.toFixed(1), icone: 'bi-geo', iconeBgClasse: 'bg-green-dim', iconeCorClasse: 'text-green' },
      { label: 'Taxa de Aprovação', valor: `${taxaAprovacao}%`, icone: 'bi-check-lg', iconeBgClasse: 'bg-green', iconeCorClasse: 'text-white' },
      { label: 'Total de Alunos', valor: `${total}`, icone: 'bi-people', iconeBgClasse: 'bg-blue', iconeCorClasse: 'text-white' },
      { label: 'Alunos em Risco', valor: `${emRisco}`, icone: 'bi-exclamation-triangle', iconeBgClasse: 'bg-amber', iconeCorClasse: 'text-white' },
    ];
  }

  // ── Distribuição de notas (derivada) ──────────────────────────────────
  get distribuicaoNotas(): BarraDistribuicao[] {
    const lista = this.alunos();
    const faixas = [
      { faixa: '0-4', corClasse: 'bg-danger', teste: (m: number) => m < 4 },
      { faixa: '4-6', corClasse: 'bg-amber', teste: (m: number) => m >= 4 && m < 6 },
      { faixa: '6-8', corClasse: 'bg-blue', teste: (m: number) => m >= 6 && m < 8 },
      { faixa: '8-10', corClasse: 'bg-green', teste: (m: number) => m >= 8 },
    ];
    const medias = lista.map((a) => this.media(a));
    const maxCount = Math.max(1, ...faixas.map((f) => medias.filter(f.teste).length));
    return faixas.map((f) => ({
      faixa: f.faixa,
      corClasse: f.corClasse,
      altura: Math.round((medias.filter(f.teste).length / maxCount) * 100),
    }));
  }

  // Distribuição de notas como Chart.js (ng2-charts) — padroniza os gráficos "à mão"
  readonly barChartType = 'bar' as const;

  get distChartData(): ChartConfiguration<'bar'>['data'] {
    const medias = this.alunos().map((a) => this.media(a));
    const faixas: [string, (m: number) => boolean, string][] = [
      ['0-4', (m) => m < 4, '#ef4444'],
      ['4-6', (m) => m >= 4 && m < 6, '#f59e0b'],
      ['6-8', (m) => m >= 6 && m < 8, '#2196f3'],
      ['8-10', (m) => m >= 8, '#06d25e'],
    ];
    return {
      labels: faixas.map((f) => f[0]),
      datasets: [
        {
          label: 'Alunos',
          data: faixas.map((f) => medias.filter(f[1]).length),
          backgroundColor: faixas.map((f) => f[2]),
          borderRadius: 6,
        },
      ],
    };
  }

  readonly distChartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { beginAtZero: true, ticks: { stepSize: 1, color: '#8a99ad' }, grid: { color: 'rgba(255,255,255,0.05)' } },
      x: { ticks: { color: '#8a99ad' }, grid: { display: false } },
    },
  };

  salvarAlteracoes(): void {
    if (this.salvando()) return;
    const lista = this.alunos();
    if (!lista.length) return;
    this.salvando.set(true);

    const chamadas = lista.map((a) =>
      this.alunosApi.editarNotas(a.alunoId, {
        disciplina: a.disciplina,
        periodo: a.periodo,
        p1: this.num(a.p1) ?? undefined,
        p2: this.num(a.p2) ?? undefined,
        t1: this.num(a.t1) ?? undefined,
        participacao: this.num(a.part) ?? undefined,
      }),
    );

    forkJoin(chamadas).subscribe({
      next: () => {
        this.salvando.set(false);
        this.mensagemSucesso = '✅ Alterações salvas com sucesso!';
        setTimeout(() => (this.mensagemSucesso = ''), 3000);
      },
      error: () => {
        this.salvando.set(false);
        this.mensagemSucesso = '❌ Falha ao salvar as notas.';
        setTimeout(() => (this.mensagemSucesso = ''), 3000);
      },
    });
  }

  private fmt(v: number | null): string {
    return v == null ? '' : String(v).replace('.', ',');
  }

  private num(v: string): number | null {
    if (!v || !v.trim()) return null;
    const n = Number(v.replace(',', '.'));
    return isNaN(n) ? null : n;
  }
}
