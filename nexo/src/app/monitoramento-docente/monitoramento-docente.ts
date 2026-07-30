import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartOptions } from 'chart.js';
import { GestaoDiretorService } from '../api/gestao-diretor.service';
import { ProfessorMonitorDTO } from '../core/api.models';
import { resolverFoto } from '../core/avatar';

/** Professor no formato de exibição do monitoramento (derivado do DTO do backend). */
interface ProfessorView {
  /** Conta de login do docente — o destinatário do chat. Nulo se ele ainda não tem. */
  usuarioId: number | null;
  nome: string;
  materia: string;
  status: string;
  statusClasse: string;
  turmas: string;
  pendentes: number;
  tempo: number;
  interacoes: number;
  avaliacao: number;
  tarefasConcluidas: number;
  tarefasTotal: number;
  foto: string;
}

interface TopPerformer {
  nome: string;
  materia: string;
  nota: number;
  foto: string;
}


const STATUS_CLASSE: Record<string, string> = {
  Excelente: 'excelente',
  Bom: 'bom',
  Atenção: 'atencao',
};

@Component({
  selector: 'app-monitoramento-docente',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './monitoramento-docente.html',
  styleUrl: './monitoramento-docente.scss',
})
export class MonitoramentoDocente {
  private readonly gestao = inject(GestaoDiretorService);
  private readonly router = inject(Router);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly professores = signal<ProfessorView[]>([]);

  // Top performers: os 3 docentes com maior avaliação
  readonly topPerformers = computed<TopPerformer[]>(() =>
    this.professores()
      .slice(0, 3)
      .map((p) => ({ nome: p.nome.replace(/^Prof[a]?\.\s*/, ''), materia: p.materia, nota: p.avaliacao, foto: p.foto }))
  );

  // KPIs agregados da lista real (antes eram fixos no template)
  readonly totalPendentes = computed(() => this.professores().reduce((s, p) => s + p.pendentes, 0));
  readonly percentualEmDia = computed(() => {
    const lista = this.professores();
    if (!lista.length) return 0;
    const concluidas = lista.reduce((s, p) => s + p.tarefasConcluidas, 0);
    const total = lista.reduce((s, p) => s + p.tarefasTotal, 0);
    return total ? Math.round((concluidas / total) * 100) : 0;
  });
  readonly tempoMedio = computed(() => {
    const lista = this.professores();
    if (!lista.length) return 0;
    const media = lista.reduce((s, p) => s + p.tempo, 0) / lista.length;
    return Math.round(media * 10) / 10;
  });
  readonly interacoesSemanais = computed(() => this.professores().reduce((s, p) => s + p.interacoes, 0));

  // Gráfico de produtividade por docente (Chart.js — substitui o mock estático)
  readonly barChartType = 'bar' as const;

  readonly chartData = computed<ChartConfiguration<'bar'>['data']>(() => {
    const lista = this.professores();
    const nomes = lista.map((p) => p.nome.replace(/^Prof[a]?\.\s*/, ''));
    return {
      labels: nomes,
      datasets: [
        { label: 'Interações', data: lista.map((p) => p.interacoes), backgroundColor: '#2196f3', borderRadius: 6 },
        { label: 'Correções pendentes', data: lista.map((p) => p.pendentes), backgroundColor: '#f59e0b', borderRadius: 6 },
        { label: 'Tarefas concluídas', data: lista.map((p) => p.tarefasConcluidas), backgroundColor: '#06d25e', borderRadius: 6 },
      ],
    };
  });

  readonly chartOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { color: '#8a99ad', usePointStyle: true } } },
    scales: {
      y: { beginAtZero: true, ticks: { color: '#8a99ad' }, grid: { color: 'rgba(255,255,255,0.05)' } },
      x: { ticks: { color: '#8a99ad' }, grid: { display: false } },
    },
  };

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.gestao.monitoramentoProfessores().subscribe({
      next: (lista) => {
        this.professores.set(lista.map((dto) => this.paraView(dto)));
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Abre a conversa com o docente; o chat pré-seleciona pelo id que vai na URL. */
  conversarCom(prof: ProfessorView): void {
    if (prof.usuarioId === null) return;
    this.router.navigate(['/mensagens'], { queryParams: { com: prof.usuarioId } });
  }

  private paraView(dto: ProfessorMonitorDTO): ProfessorView {
    return {
      usuarioId: dto.usuarioId,
      nome: dto.nome,
      materia: dto.disciplina,
      status: dto.status,
      statusClasse: STATUS_CLASSE[dto.status] ?? 'bom',
      turmas: dto.turmas ?? '—',
      pendentes: dto.correcoesPendentes,
      tempo: dto.tempoRespostaDias,
      interacoes: dto.interacoesSemana,
      avaliacao: dto.avaliacao,
      tarefasConcluidas: dto.tarefasConcluidas,
      tarefasTotal: dto.tarefasTotal,
      foto: resolverFoto(dto.foto),
    };
  }
}
