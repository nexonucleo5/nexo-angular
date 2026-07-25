import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { ProfessorDashboardService } from '../api/professor-dashboard.service';
import { ProfessorDashboardDTO } from '../core/api.models';
import { resolverFoto } from '../core/avatar';

const COR_PROGRESSO = ['blue-fill', 'purple-fill', 'green-fill', 'orange-fill'];

@Component({
  selector: 'app-dashboard-professor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-professor.html',
  styleUrl: './dashboard-professor.scss',
})
export class DashboardProfessor {
  private readonly service = inject(ProfessorDashboardService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly dados = signal<ProfessorDashboardDTO | null>(null);

  readonly stats = computed(() => {
    const d = this.dados();
    if (!d) return [];
    return [
      { label: 'Turmas Ativas', value: `${d.turmasAtivas}`, icon: 'bi-people-fill', color: 'blue', tendencia: '' },
      { label: 'Total de Alunos', value: `${d.totalAlunos}`, icon: 'bi-person-check-fill', color: 'green', tendencia: `em ${d.turmasAtivas} turmas` },
      { label: 'Correções Pendentes', value: `${d.correcoesPendentes}`, icon: 'bi-clock-history', color: 'orange', tendencia: '' },
      { label: 'Avaliações Este Mês', value: `${d.avaliacoesMes}`, icon: 'bi-journal-check', color: 'purple', tendencia: '' },
    ];
  });

  readonly proximasAulas = computed(() =>
    (this.dados()?.proximasAulas ?? []).map((a) => ({
      turma: a.turma,
      disciplina: a.disciplina,
      hora: a.hora,
      sala: a.sala,
      alunos: a.alunos,
      data: this.formatarDia(a.data),
    }))
  );

  readonly alunosAtencao = computed(() =>
    (this.dados()?.alunosAtencao ?? []).map((a) => ({
      nome: a.nome,
      turma: a.turma,
      mediaAtual: a.mediaAtual,
      frequencia: a.frequencia,
      status: a.status,
      foto: resolverFoto(a.foto),
      ultimaAtividade: this.formatarRelativo(a.ultimaAtividade),
    }))
  );

  readonly turmas = computed(() =>
    (this.dados()?.turmas ?? []).map((t, i) => ({
      nome: t.nome,
      disciplina: t.disciplina,
      alunos: t.alunos,
      mediaGeral: t.mediaGeral,
      progresso: t.progresso,
      corProgresso: COR_PROGRESSO[i % COR_PROGRESSO.length],
    }))
  );

  readonly atividadesRecentes = computed(() =>
    (this.dados()?.atividades ?? []).map((a) => ({
      descricao: a.descricao,
      turma: a.turma,
      icone: a.icone,
      corIcone: a.cor,
      tempo: this.formatarRelativo(a.criadaEm),
    }))
  );

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.dashboard().subscribe({
      next: (d) => {
        this.dados.set(d);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  getClasseFrequencia(freq: number): string {
    if (freq >= 85) return 'text-success';
    if (freq >= 70) return 'text-warning';
    return 'text-danger';
  }

  /** ISO de data → "Hoje" / "Amanhã" / dd/MM. */
  private formatarDia(iso: string): string {
    const data = new Date(iso + 'T00:00:00');
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    const dias = Math.round((data.getTime() - hoje.getTime()) / 86_400_000);
    if (dias === 0) return 'Hoje';
    if (dias === 1) return 'Amanhã';
    if (dias === -1) return 'Ontem';
    return data.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  }

  private formatarRelativo(iso: string | null): string {
    if (!iso) return '—';
    const ms = Date.now() - new Date(iso).getTime();
    const horas = Math.floor(ms / 3_600_000);
    if (horas < 1) return 'agora há pouco';
    if (horas < 24) return `há ${horas}h`;
    const dias = Math.floor(horas / 24);
    return dias === 1 ? 'ontem' : `há ${dias} dias`;
  }
}
