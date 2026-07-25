import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProfessorDashboardService } from '../api/professor-dashboard.service';
import { ProfessorDashboardDTO } from '../core/api.models';
import { resolverFoto } from '../core/avatar';

/**
 * Corpo da tela de perfil quando o usuário logado é professor.
 * O cabeçalho (foto, nome, edição) fica em Perfil; aqui entram apenas os
 * dados docentes, vindos de GET /api/professor/dashboard.
 */
@Component({
  selector: 'app-perfil-professor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './perfil-professor.html',
  styleUrl: './perfil-professor.scss',
})
export class PerfilProfessor {
  private readonly dashboard = inject(ProfessorDashboardService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly dados = signal<ProfessorDashboardDTO | null>(null);

  readonly turmas = computed(() => this.dados()?.turmas ?? []);
  readonly alunosAtencao = computed(() => this.dados()?.alunosAtencao ?? []);

  /** KPIs docentes; espelham os cards do dashboard do professor. */
  readonly indicadores = computed(() => {
    const d = this.dados();
    if (!d) return [];
    return [
      { valor: `${d.turmasAtivas}`, label: 'Turmas Ativas', icone: 'bi-people-fill', cor: 'purple' },
      { valor: `${d.totalAlunos}`, label: 'Alunos', icone: 'bi-mortarboard-fill', cor: 'blue' },
      { valor: `${d.correcoesPendentes}`, label: 'Correções Pendentes', icone: 'bi-pencil-square', cor: 'orange' },
      { valor: `${d.avaliacoesMes}`, label: 'Avaliações no Mês', icone: 'bi-clipboard-check', cor: 'green' },
    ];
  });

  /** Média das turmas do professor, em escala 0-10. */
  readonly mediaGeral = computed(() => {
    const lista = this.turmas();
    if (!lista.length) return 0;
    const soma = lista.reduce((acc, t) => acc + t.mediaGeral, 0);
    return Math.round((soma / lista.length) * 10) / 10;
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.dashboard.dashboard().subscribe({
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

  /** Foto do aluno ou o avatar padrão, resolvendo a URL das fotos enviadas. */
  fotoDe(foto: string | null): string {
    return resolverFoto(foto);
  }

  /** Barra colorida por faixa de média (mesma régua da tela de relatórios). */
  corDaMedia(media: number): string {
    if (media < 6) return 'red-fill';
    if (media < 7) return 'orange-fill';
    return 'green-fill';
  }
}
