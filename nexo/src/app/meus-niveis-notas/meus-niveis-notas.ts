import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { AlunoDashboardService } from '../api/aluno-dashboard.service';
import { AlunoDashboardDTO, AlunoNotaDTO } from '../core/api.models';

interface StatNivel {
  icone: string;
  classeIcone: string;
  label: string;
  valor: string;
}

interface MateriaNota {
  nome: string;
  percentual: number;
  nota: number;
  classeProgresso: string;
}

interface AtividadeRecente {
  titulo: string;
  materia: string;
  data: string;
  xp: number;
}

const CORES_MATERIA = ['bio', 'mat', 'his', 'ing'];

function tituloNivel(nivel: number): string {
  if (nivel < 5) return 'Iniciante';
  if (nivel < 10) return 'Estudante Dedicado';
  if (nivel < 15) return 'Avançado';
  return 'Mestre dos Estudos';
}

@Component({
  selector: 'app-meus-niveis-notas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meus-niveis-notas.html',
  styleUrl: './meus-niveis-notas.scss',
})
export class MeusNiveisNotas {
  private readonly api = inject(AlunoDashboardService);

  readonly carregando = signal(true);
  private readonly dash = signal<AlunoDashboardDTO | null>(null);
  private readonly notas = signal<AlunoNotaDTO[]>([]);

  readonly nivel = computed(() => {
    const d = this.dash();
    const xpTotal = d?.xpTotal ?? 0;
    const nivelAtual = d?.nivel ?? 1;
    return {
      atual: nivelAtual,
      titulo: tituloNivel(nivelAtual),
      xpAtual: xpTotal % 400,
      xpTotal: 400,
      proximoNivel: nivelAtual + 1,
    };
  });

  readonly xpPercentual = computed(() => Math.round((this.nivel().xpAtual / this.nivel().xpTotal) * 100));
  readonly xpFaltam = computed(() => this.nivel().xpTotal - this.nivel().xpAtual);

  readonly stats = computed<StatNivel[]>(() => {
    const medias = this.notas().map((n) => n.media).filter((m): m is number => m != null);
    const mediaGeral = medias.length ? (medias.reduce((s, m) => s + m, 0) / medias.length).toFixed(1) : '—';
    const melhor = medias.length ? Math.max(...medias).toFixed(1) : '—';
    const d = this.dash();
    return [
      { icone: '📊', classeIcone: 'roxo', label: 'Média Geral', valor: mediaGeral },
      { icone: '📈', classeIcone: 'verde', label: 'Melhor Nota', valor: melhor },
      { icone: '⚡', classeIcone: 'azul', label: 'XP Total', valor: `${d?.xpTotal ?? 0}` },
      { icone: '🏆', classeIcone: 'laranja', label: 'Ranking', valor: d ? `#${d.posicao}` : '—' },
    ];
  });

  readonly materias = computed<MateriaNota[]>(() =>
    this.notas().map((n, i) => ({
      nome: n.disciplina,
      nota: n.media ?? 0,
      percentual: Math.round((n.media ?? 0) * 10),
      classeProgresso: CORES_MATERIA[i % CORES_MATERIA.length],
    })),
  );

  readonly atividades = computed<AtividadeRecente[]>(() =>
    (this.dash()?.atividades ?? []).map((a) => ({
      titulo: a.titulo,
      materia: a.materia,
      data: this.formatarData(a.criadaEm),
      xp: a.xp,
    })),
  );

  constructor() {
    forkJoin({ dash: this.api.dashboard(), notas: this.api.notas() }).subscribe({
      next: ({ dash, notas }) => {
        this.dash.set(dash);
        this.notas.set(notas);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  private formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
