import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { ChartConfiguration, ChartOptions, ChartType } from 'chart.js';
import { forkJoin } from 'rxjs';
import { GestaoDiretorService } from '../api/gestao-diretor.service';
import { AlunoRiscoDTO, DesempenhoDTO, ProfessorMonitorDTO } from '../core/api.models';

interface MetricView {
  label: string;
  value: string;
  icon: string;
  color: string;
}

interface AlunoRiscoView {
  nome: string;
  turma: string;
  frequencia: number;
  risco: string;
  foto: string;
  tempo: string;
}

interface AlertaView {
  titulo: string;
  desc: string;
  tempo: string;
  nivel: string;
}

const FOTO_PADRAO = 'assets/imagensProjeto/gabrielZapelini.png';

@Component({
  selector: 'app-dashboard-diretor',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './diretor-dashboard.html',
  styleUrl: './diretor-dashboard.scss',
})
export class DashboardDiretor {
  private readonly gestao = inject(GestaoDiretorService);

  readonly carregando = signal(true);
  readonly erro = signal(false);

  private readonly desempenho = signal<DesempenhoDTO | null>(null);
  private readonly risco = signal<AlunoRiscoDTO[]>([]);
  private readonly docentes = signal<ProfessorMonitorDTO[]>([]);

  readonly metrics = computed<MetricView[]>(() => {
    const d = this.desempenho();
    if (!d) return [];
    const lista = this.risco();
    const total = lista.length || d.totalAlunos;
    const emRiscoAlto = lista.filter((a) => a.risco === 'ALTO').length;
    const taxaEvasao = total ? Math.round((emRiscoAlto / total) * 1000) / 10 : 0;
    return [
      { label: 'Total de Alunos', value: `${d.totalAlunos}`, icon: 'bi-people-fill', color: 'blue' },
      { label: 'Taxa de Evasão (risco alto)', value: `${taxaEvasao}%`, icon: 'bi-person-x-fill', color: 'red' },
      { label: 'Engajamento Médio', value: `${d.engajamentoMedio}%`, icon: 'bi-bullseye', color: 'green' },
      { label: 'Desempenho Geral', value: `${d.mediaGeral}`, icon: 'bi-graph-up-arrow', color: 'purple' },
    ];
  });

  readonly alunosRisco = computed<AlunoRiscoView[]>(() =>
    this.risco()
      .filter((a) => a.risco !== 'BAIXO')
      .slice(0, 3)
      .map((a) => ({
        nome: a.nome,
        turma: a.turma ?? 'Sem turma',
        frequencia: Math.max(0, Math.round(100 - a.percentualFaltas)),
        risco: a.risco.toLowerCase(),
        foto: a.foto || FOTO_PADRAO,
        tempo: this.formatarRelativo(a.ultimoAcessoEm),
      }))
  );

  readonly alertas = computed<AlertaView[]>(() => {
    const lista = this.risco();
    const alto = lista.filter((a) => a.risco === 'ALTO').length;
    const baixaFreq = lista.filter((a) => a.percentualFaltas > 40).length;
    const pendentes = this.docentes().reduce((s, p) => s + p.correcoesPendentes, 0);
    const alertas: AlertaView[] = [];
    if (alto > 0) {
      alertas.push({
        titulo: 'Risco de Evasão',
        desc: `${alto} aluno(s) com risco alto de evasão`,
        tempo: 'Agora',
        nivel: 'alta',
      });
    }
    if (pendentes > 0) {
      alertas.push({
        titulo: 'Correções Pendentes',
        desc: `${pendentes} correções aguardando os professores`,
        tempo: 'Hoje',
        nivel: pendentes > 20 ? 'alta' : 'media',
      });
    }
    if (baixaFreq > 0) {
      alertas.push({
        titulo: 'Baixa Frequência',
        desc: `${baixaFreq} aluno(s) com mais de 40% de faltas`,
        tempo: 'Hoje',
        nivel: 'alta',
      });
    }
    return alertas;
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    forkJoin({
      desempenho: this.gestao.desempenho(),
      risco: this.gestao.riscoEvasao(),
      docentes: this.gestao.monitoramentoProfessores(),
    }).subscribe({
      next: ({ desempenho, risco, docentes }) => {
        this.desempenho.set(desempenho);
        this.risco.set(risco);
        this.docentes.set(docentes);
        this.atualizarGrafico(desempenho);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  private formatarRelativo(iso: string | null): string {
    if (!iso) return '—';
    const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
    if (dias <= 0) return 'Hoje';
    if (dias === 1) return 'Ontem';
    if (dias < 7) return `Há ${dias} dias`;
    return `Há ${Math.floor(dias / 7)} semana(s)`;
  }

  // ── Gráfico: desempenho por turma (média x10 e frequência) vindo do backend ──
  public lineChartType: ChartType = 'bar';

  public lineChartData: ChartConfiguration['data'] = { labels: [], datasets: [] };

  private atualizarGrafico(d: DesempenhoDTO): void {
    this.lineChartData = {
      labels: d.turmas.map((t) => t.turma),
      datasets: [
        {
          label: 'Média (x10)',
          data: d.turmas.map((t) => Math.round(t.media * 10)),
          backgroundColor: 'rgba(33, 150, 243, 0.7)',
          borderRadius: 6,
        },
        {
          label: 'Frequência (%)',
          data: d.turmas.map((t) => t.frequencia),
          backgroundColor: 'rgba(6, 210, 94, 0.7)',
          borderRadius: 6,
        },
      ],
    };
  }

  public lineChartOptions: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: { color: '#8a99ad', usePointStyle: true, pointStyle: 'circle', padding: 20 },
      },
      tooltip: {
        enabled: true,
        mode: 'index',
        intersect: false,
        backgroundColor: '#1e293b',
        titleColor: '#fff',
        bodyColor: '#fff',
        borderColor: '#334155',
        borderWidth: 1,
      },
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        ticks: { stepSize: 25, color: '#8a99ad' },
        grid: { color: 'rgba(255, 255, 255, 0.05)' },
      },
      x: { ticks: { color: '#8a99ad' }, grid: { display: false } },
    },
  };
}
