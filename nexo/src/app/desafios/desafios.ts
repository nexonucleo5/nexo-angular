import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DesafioDTO, DesafiosService } from '../api/desafios.service';

interface DesafioView {
  id: number;
  titulo: string;
  materia: string;
  nivel: string;
  nivelClasse: string;
  xp: number;
  tempo: string;
  status: 'aberto' | 'progresso' | 'concluido';
  progresso: number;
}

const NIVEL_LABEL: Record<string, string> = { FACIL: 'Fácil', MEDIO: 'Médio', DIFICIL: 'Difícil' };
const NIVEL_CLASSE: Record<string, string> = { FACIL: 'facil', MEDIO: 'medio', DIFICIL: 'dificil' };
const STATUS_MAP: Record<string, 'aberto' | 'progresso' | 'concluido'> = {
  ABERTO: 'aberto',
  PROGRESSO: 'progresso',
  CONCLUIDO: 'concluido',
};

@Component({
  selector: 'app-desafios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './desafios.html',
  styleUrl: './desafios.scss',
})
export class Desafios {
  private readonly api = inject(DesafiosService);
  private readonly router = inject(Router);

  /**
   * Os três filtros são signals, e não campos comuns, porque {@link desafiosFiltrados}
   * é um computed: ele só recalcula quando um signal do qual depende muda. Como
   * propriedades simples, digitar na busca ou trocar o select não invalidava o
   * cache — a lista continuava mostrando o resultado antigo e o filtro parecia
   * morto. É o mesmo motivo pelo qual a tela de matérias foi convertida.
   */
  readonly buscaDeTermos = signal('');
  readonly materiaSelecionada = signal('Todas as Matérias');
  readonly nivelSelecionado = signal('Todos os Níveis');
  readonly statusSelecionado = signal<'todos' | 'aberto' | 'progresso' | 'concluido'>('todos');

  readonly carregando = signal(true);
  private readonly desafios = signal<DesafioView[]>([]);

  readonly stats = signal([
    { label: 'Desafios Concluídos', value: '0', icon: 'bi-trophy', color: 'orange' },
    { label: 'Taxa de Sucesso', value: '0%', icon: 'bi-bullseye', color: 'green' },
    { label: 'Sequência Atual', value: '0 dias', icon: 'bi-fire', color: 'red' },
  ]);

  readonly materias = computed(() => [
    'Todas as Matérias',
    ...Array.from(new Set(this.desafios().map((d) => d.materia))).sort(),
  ]);
  readonly niveis = ['Todos os Níveis', 'Fácil', 'Médio', 'Difícil'];

  readonly statusOpcoes = [
    { valor: 'todos' as const, rotulo: 'Todos' },
    { valor: 'aberto' as const, rotulo: 'Não iniciados' },
    { valor: 'progresso' as const, rotulo: 'Em andamento' },
    { valor: 'concluido' as const, rotulo: 'Concluídos' },
  ];

  readonly desafiosFiltrados = computed(() => {
    const termo = this.buscaDeTermos().trim().toLowerCase();
    const materia = this.materiaSelecionada();
    const nivel = this.nivelSelecionado();
    const status = this.statusSelecionado();
    return this.desafios().filter(
      (d) =>
        d.titulo.toLowerCase().includes(termo) &&
        (materia === 'Todas as Matérias' || d.materia === materia) &&
        (nivel === 'Todos os Níveis' || d.nivel === nivel) &&
        (status === 'todos' || d.status === status),
    );
  });

  /** Algum filtro em uso — controla o aviso de "nenhum resultado" e o botão de limpar. */
  readonly temFiltro = computed(() =>
    this.buscaDeTermos().trim() !== '' ||
    this.materiaSelecionada() !== 'Todas as Matérias' ||
    this.nivelSelecionado() !== 'Todos os Níveis' ||
    this.statusSelecionado() !== 'todos',
  );

  limparFiltros(): void {
    this.buscaDeTermos.set('');
    this.materiaSelecionada.set('Todas as Matérias');
    this.nivelSelecionado.set('Todos os Níveis');
    this.statusSelecionado.set('todos');
  }

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.listar().subscribe({
      next: (resp) => {
        this.desafios.set(resp.desafios.map((d) => this.paraView(d)));
        this.stats.set([
          { label: 'Desafios Concluídos', value: `${resp.stats.concluidos}`, icon: 'bi-trophy', color: 'orange' },
          { label: 'Taxa de Sucesso', value: `${resp.stats.taxaSucesso}%`, icon: 'bi-bullseye', color: 'green' },
          { label: 'Sequência Atual', value: `${resp.stats.sequenciaDias} dias`, icon: 'bi-fire', color: 'red' },
        ]);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  private paraView(d: DesafioDTO): DesafioView {
    return {
      id: d.id,
      titulo: d.titulo,
      materia: d.materia,
      nivel: NIVEL_LABEL[d.nivel] ?? d.nivel,
      nivelClasse: NIVEL_CLASSE[d.nivel] ?? 'medio',
      xp: d.xp,
      tempo: `${d.tempoMin} min`,
      status: STATUS_MAP[d.status] ?? 'aberto',
      progresso: d.progresso,
    };
  }

  rotuloBotao(d: DesafioView): string {
    if (d.status === 'concluido') return 'Ver Resultado';
    if (d.status === 'progresso') return 'Continuar';
    return 'Iniciar';
  }

  /** Abre o quiz do desafio — 'Iniciar'/'Continuar' vão direto às perguntas; 'Ver Resultado' abre em modo revisão. */
  acao(d: DesafioView): void {
    this.router.navigate(['/desafios', d.id, 'quiz']);
  }

  getClasseNivel(nivel: string): string {
    const mapa: Record<string, string> = { Fácil: 'facil', Médio: 'medio', Difícil: 'dificil' };
    return mapa[nivel] ?? 'medio';
  }
}
