import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardDiretor } from '../diretor-dashboard/diretor-dashboard';
import { AuthService } from '../services/auth.service';
import { AlunoDashboardService } from '../api/aluno-dashboard.service';
import { AlunoDashboardDTO } from '../core/api.models';

const FOTO_PADRAO = 'assets/imagensProjeto/gabrielZapelini.png';
const COR_PROGRESSO = ['blue-fill', 'green-fill', 'purple-fill', 'orange-fill'];

@Component({
  selector: 'app-dashboards',
  standalone: true,
  imports: [CommonModule, DashboardDiretor],
  templateUrl: './dashboards.html',
  styleUrl: './dashboards.scss',
})
export class Dashboards {
  public authService = inject(AuthService);
  private readonly alunoDashboard = inject(AlunoDashboardService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly dados = signal<AlunoDashboardDTO | null>(null);

  readonly nome = computed(() => this.dados()?.nome ?? '');
  readonly xpSemana = computed(() => this.dados()?.xpSemana ?? 0);
  readonly metaSemanalXp = computed(() => this.dados()?.metaSemanalXp ?? 1000);
  readonly xpPercent = computed(() => {
    const d = this.dados();
    if (!d || !d.metaSemanalXp) return 0;
    return Math.min(100, Math.round((d.xpSemana / d.metaSemanalXp) * 100));
  });
  readonly ofensivaDias = computed(() => this.dados()?.ofensivaDias ?? 0);
  readonly posicao = computed(() => this.dados()?.posicao ?? 0);
  readonly turmaNome = computed(() => this.dados()?.turmaNome ?? '');
  readonly tarefasFeitasHoje = computed(() => this.dados()?.tarefasFeitasHoje ?? 0);
  readonly tarefasHoje = computed(() => this.dados()?.tarefasHoje ?? 0);
  readonly tarefasPercent = computed(() => {
    const d = this.dados();
    if (!d || !d.tarefasHoje) return 0;
    return Math.round((d.tarefasFeitasHoje / d.tarefasHoje) * 100);
  });
  readonly tarefasRestantes = computed(() => Math.max(0, this.tarefasHoje() - this.tarefasFeitasHoje()));

  readonly atividades = computed(() =>
    (this.dados()?.atividades ?? []).map((a, i) => ({
      titulo: a.titulo,
      materia: a.materia,
      xp: a.xp,
      progresso: a.progresso,
      icone: a.icone,
      tempo: this.formatarRelativo(a.criadaEm),
      corProgresso: COR_PROGRESSO[i % COR_PROGRESSO.length],
    }))
  );

  readonly ranking = computed(() =>
    (this.dados()?.ranking ?? []).map((r) => ({
      pos: r.posicao,
      nome: r.nome,
      xp: r.xp,
      foto: r.foto || FOTO_PADRAO,
      isMe: r.isMe,
    }))
  );

  constructor() {
    if (this.authService.usuarioLogado()?.role !== 'diretor') {
      this.carregar();
    } else {
      this.carregando.set(false);
    }
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.alunoDashboard.dashboard().subscribe({
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

  private formatarRelativo(iso: string): string {
    const ms = Date.now() - new Date(iso).getTime();
    const horas = Math.floor(ms / 3_600_000);
    if (horas < 1) return 'agora há pouco';
    if (horas < 24) return `há ${horas} hora(s)`;
    const dias = Math.floor(horas / 24);
    return dias === 1 ? 'ontem' : `há ${dias} dias`;
  }
}
