import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AdminService } from '../api/admin.service';
import { ConfiguracaoAdminService } from '../configuracao-admin/configuracao-admin.service';
import { DashboardAdminDTO, MateriaCatalogoDTO } from '../core/api.models';

/**
 * Painel de quem administra o sistema de aprendizado.
 *
 * <p>Ocupou o lugar do painel da secretaria, e o assunto mudou junto: no lugar da
 * fila de matrículas, da documentação a cobrar e das vagas por turma, o centro
 * aqui é <b>quem tem acesso</b> e <b>o que está publicado</b>. A escola tem outro
 * sistema para a vida escolar; este cuida de aprendizado e retenção de conteúdo.
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './admin-dashboard.html',
  styleUrl: './admin-dashboard.scss',
})
export class AdminDashboard {
  private readonly admin = inject(AdminService);
  private readonly config = inject(ConfiguracaoAdminService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly dados = signal<DashboardAdminDTO | null>(null);
  readonly catalogo = signal<MateriaCatalogoDTO[]>([]);

  /**
   * Matérias com pouco conteúdo publicado — o item de ação do painel. Num sistema
   * cuja razão de ser é retenção, matéria vazia é aluno sem o que estudar, e é a
   * pergunta que se faz antes de qualquer outra.
   */
  readonly materiasFracas = computed(() => {
    const limite = this.config.settings().catalogo.avisarMateriaComMenosDeNConteudos;
    return this.catalogo()
      .filter((m) => m.conteudosPublicados < limite)
      .sort((a, b) => a.conteudosPublicados - b.conteudosPublicados);
  });

  /** Conteúdo fora do ar em qualquer matéria — some da tela do aluno até voltar. */
  readonly temConteudoForaDoAr = computed(() => {
    const d = this.dados();
    return !!d && (d.conteudosDespublicados > 0 || d.desafiosDespublicados > 0);
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    forkJoin({
      dashboard: this.admin.dashboard(),
      catalogo: this.admin.catalogo(),
    }).subscribe({
      next: ({ dashboard, catalogo }) => {
        this.dados.set(dashboard);
        this.catalogo.set(catalogo);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Quanto da matéria está no ar — o preenchimento da barra. */
  percentualPublicado(m: MateriaCatalogoDTO): number {
    return m.conteudos === 0 ? 0 : Math.round((m.conteudosPublicados / m.conteudos) * 100);
  }
}
