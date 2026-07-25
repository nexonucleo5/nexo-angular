import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { GestaoDiretorService } from '../api/gestao-diretor.service';
import { DesempenhoDTO, ProfessorMonitorDTO } from '../core/api.models';
import { resolverFoto } from '../core/avatar';

/**
 * Corpo da tela de perfil quando o usuário logado é diretor.
 * O cabeçalho (foto, nome, edição) fica em Perfil; aqui entra o panorama
 * institucional, vindo de /api/relatorios/desempenho e /api/monitoramento/professores.
 */
@Component({
  selector: 'app-perfil-diretor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './perfil-diretor.html',
  styleUrl: './perfil-diretor.scss',
})
export class PerfilDiretor {
  private readonly gestao = inject(GestaoDiretorService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly desempenho = signal<DesempenhoDTO | null>(null);
  private readonly docentes = signal<ProfessorMonitorDTO[]>([]);

  readonly turmas = computed(() => this.desempenho()?.turmas ?? []);

  /** KPIs institucionais — os mesmos números da tela de relatórios. */
  readonly indicadores = computed(() => {
    const d = this.desempenho();
    if (!d) return [];
    return [
      { valor: `${d.totalAlunos}`, label: 'Alunos na Rede', icone: 'bi-mortarboard-fill', cor: 'purple' },
      { valor: `${d.taxaAprovacao}%`, label: 'Taxa de Aprovação', icone: 'bi-graph-up', cor: 'green' },
      { valor: `${d.mediaGeral}`, label: 'Média Geral', icone: 'bi-star-fill', cor: 'gold' },
      { valor: `${d.frequenciaMedia}%`, label: 'Frequência Média', icone: 'bi-calendar-check', cor: 'blue' },
    ];
  });

  /** Corpo docente ordenado por avaliação, limitado aos cinco primeiros. */
  readonly topDocentes = computed(() => this.docentes().slice(0, 5));

  readonly totalDocentes = computed(() => this.docentes().length);

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    forkJoin({
      desempenho: this.gestao.desempenho(),
      docentes: this.gestao.monitoramentoProfessores(),
    }).subscribe({
      next: ({ desempenho, docentes }) => {
        this.desempenho.set(desempenho);
        this.docentes.set(docentes);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Foto do docente ou o avatar padrão, resolvendo a URL das fotos enviadas. */
  fotoDe(foto: string | null): string {
    return resolverFoto(foto);
  }

  /** Barra colorida por faixa de média (mesma régua da tela de relatórios). */
  corDaMedia(media: number): string {
    if (media < 6) return 'red-fill';
    if (media < 7) return 'orange-fill';
    return 'green-fill';
  }

  /** Classe da etiqueta a partir do status calculado pelo backend. */
  classeStatus(status: string): string {
    const normalizado = status.toLowerCase();
    if (normalizado === 'excelente') return 'excelente';
    if (normalizado === 'bom') return 'bom';
    return 'atencao';
  }
}
