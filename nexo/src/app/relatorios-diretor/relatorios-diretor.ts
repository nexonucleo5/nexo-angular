import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { GestaoDiretorService } from '../api/gestao-diretor.service';
import { DesempenhoDTO } from '../core/api.models';

interface StatRelatorio {
  label: string;
  value: string;
  icon: string;
  color: string;
}

interface DisciplinaDesempenho {
  nome: string;
  valor: number;
  status: 'normal' | 'critico';
}

interface Gargalo {
  turma: string;
  info: string;
  media: number;
  nivel: 'Alta' | 'Média';
}

@Component({
  selector: 'app-relatorios-diretor',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './relatorios-diretor.html',
  styleUrl: './relatorios-diretor.scss',
})
export class RelatoriosDiretor {
  private readonly gestao = inject(GestaoDiretorService);

  readonly periodoSelecionado = signal('Este Bimestre');
  readonly visaoSelecionada = signal('Visão Geral');
  readonly periodos = ['Este Bimestre', 'Este Semestre', 'Geral'];
  readonly visoes = ['Visão Geral', 'Pedagógico', 'Financeiro'];

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly exportando = signal(false);
  private readonly desempenho = signal<DesempenhoDTO | null>(null);

  /** KPIs reais calculados pelo backend (antes eram números fixos: NPS, egressos, ouvidoria). */
  readonly stats = computed<StatRelatorio[]>(() => {
    const d = this.desempenho();
    if (!d) return [];
    return [
      { label: 'Taxa de Aprovação', value: `${d.taxaAprovacao}%`, icon: 'bi-graph-up', color: 'green' },
      { label: 'Média Geral', value: `${d.mediaGeral}`, icon: 'bi-star', color: 'orange' },
      { label: 'Frequência Média', value: `${d.frequenciaMedia}%`, icon: 'bi-calendar-check', color: 'blue' },
      { label: 'Engajamento Médio', value: `${d.engajamentoMedio}%`, icon: 'bi-activity', color: 'purple' },
    ];
  });

  /** Barras por turma (média em escala 0-100; crítico quando média < 6). */
  readonly disciplinasDesempenho = computed<DisciplinaDesempenho[]>(() =>
    (this.desempenho()?.turmas ?? []).map((t) => ({
      nome: t.turma,
      valor: Math.round(t.media * 10),
      status: t.media < 6 ? 'critico' : 'normal',
    }))
  );

  /** Turmas gargalo: menor média primeiro, apenas as abaixo de 7.0. */
  readonly gargalos = computed<Gargalo[]>(() =>
    (this.desempenho()?.turmas ?? [])
      .filter((t) => t.media < 7)
      .sort((a, b) => a.media - b.media)
      .map((t) => ({
        turma: t.turma,
        info: `${t.alunos} alunos • Frequência ${t.frequencia}%`,
        media: t.media,
        nivel: t.media < 6 ? 'Alta' : 'Média',
      }))
  );

  readonly totalAlunos = computed(() => this.desempenho()?.totalAlunos ?? 0);

  // Gráfico de desempenho por turma (Chart.js — padroniza o gráfico "à mão")
  readonly barChartType = 'bar' as const;

  readonly chartData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const turmas = this.desempenho()?.turmas ?? [];
    return {
      labels: turmas.map((t) => t.turma),
      datasets: [
        {
          label: 'Média (x10)',
          data: turmas.map((t) => Math.round(t.media * 10)),
          backgroundColor: turmas.map((t) => (t.media < 6 ? '#ef4444' : '#2ec4b6')),
          borderRadius: 6,
        },
        {
          label: 'Frequência (%)',
          data: turmas.map((t) => t.frequencia),
          backgroundColor: 'rgba(155,47,255,0.55)',
          borderRadius: 6,
        },
      ],
    };
  });

  readonly chartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { color: '#8a99ad', usePointStyle: true } } },
    scales: {
      y: { beginAtZero: true, max: 100, ticks: { color: '#8a99ad' }, grid: { color: 'rgba(255,255,255,0.05)' } },
      x: { ticks: { color: '#8a99ad' }, grid: { display: false } },
    },
  };

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.gestao.desempenho().subscribe({
      next: (dados) => {
        this.desempenho.set(dados);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Exportação real (PDF/XLSX) — antes os botões não tinham handler. */
  exportar(formato: 'pdf' | 'xlsx'): void {
    if (this.exportando()) return;
    this.exportando.set(true);
    // Não repassa periodo/visao: os rótulos da tela ("Este Bimestre"…) não correspondem
    // aos valores gravados em Nota.periodo ("2026-1"), e enviá-los zera todas as médias
    // do relatório. Ligar o filtro exige antes definir o vocabulário de período.
    this.gestao.exportarDesempenho(formato).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `relatorio-desempenho.${formato}`;
        link.click();
        URL.revokeObjectURL(url);
        this.exportando.set(false);
      },
      error: () => this.exportando.set(false),
    });
  }
}
