import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SUBJECTS } from '../materias/materias.data';
import { MateriasService } from '../api/materias.service';
import { AlunoDashboardService } from '../api/aluno-dashboard.service';
import { ConteudoMateriaDTO } from '../core/api.models';

/**
 * Detalhe de uma disciplina.
 *
 * <p>A rota recebe o id real da matéria (antes era um slug do mock). Conteúdos e
 * progresso vêm do servidor; ícone e cor continuam saindo de materias.data.ts,
 * casados pelo nome, porque são apresentação e não dado acadêmico.
 */
@Component({
  selector: 'app-disciplina-detalhe',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './disciplina-detalhe.html',
  styleUrl: './disciplina-detalhe.scss',
})
export class DisciplinaDetalhe {
  private readonly materiasService = inject(MateriasService);
  private readonly alunoApi = inject(AlunoDashboardService);

  /** Id da matéria, vindo de /disciplina/:id (withComponentInputBinding). */
  readonly id = input<string>('');

  readonly carregandoConteudo = signal(true);
  readonly conteudos = signal<ConteudoMateriaDTO[]>([]);
  readonly topicoSelecionado = signal<ConteudoMateriaDTO | null>(null);

  /** Nome da matéria conforme o servidor — é ele que casa com a apresentação local. */
  private readonly nome = signal('');
  /** Ids concluídos pelo aluno; o Set deixa a checagem por item barata no template. */
  private readonly concluidos = signal<ReadonlySet<number>>(new Set());
  readonly salvandoId = signal<number | null>(null);

  readonly disciplina = computed(() => {
    const nome = this.nome();
    if (!nome) return null;
    const local = SUBJECTS.find((s) => s.title.toLowerCase() === nome.toLowerCase());
    return {
      title: nome,
      iconClass: local?.iconClass ?? 'bi-journal-text',
      iconText: local?.iconText,
      colorClass: local?.colorClass ?? 'subj-blue',
      topics: local?.topics ?? [],
    };
  });

  readonly totalConteudos = computed(() => this.conteudos().length);
  readonly totalConcluidos = computed(
    () => this.conteudos().filter((c) => this.concluidos().has(c.id)).length,
  );
  readonly percentual = computed(() => {
    const total = this.totalConteudos();
    return total === 0 ? 0 : Math.round((this.totalConcluidos() * 100) / total);
  });

  estaConcluido(c: ConteudoMateriaDTO): boolean {
    return this.concluidos().has(c.id);
  }

  constructor() {
    // Precisa ser um effect, e não código solto no construtor: `id` é um signal
    // input preenchido pelo withComponentInputBinding depois da construção.
    // Como effect, também recarrega ao trocar de disciplina sem sair da rota.
    effect(() => {
      const materiaId = Number(this.id());
      this.topicoSelecionado.set(null);
      this.conteudos.set([]);
      this.concluidos.set(new Set());
      this.nome.set('');

      if (!materiaId) {
        this.carregandoConteudo.set(false);
        return;
      }
      untracked(() => this.carregar(materiaId));
    });
  }

  private carregar(materiaId: number): void {
    this.carregandoConteudo.set(true);

    // O nome sai da listagem do aluno, que já é recortada pela etapa dele: pedir
    // uma matéria fora da etapa simplesmente não a encontra aqui, e o servidor
    // recusa o conteúdo de qualquer jeito.
    this.alunoApi.materias().subscribe({
      next: (lista) => this.nome.set(lista.find((m) => m.id === materiaId)?.nome ?? ''),
      error: () => this.nome.set(''),
    });

    this.materiasService.listarConteudos(materiaId).subscribe({
      next: (conteudos) => {
        this.conteudos.set(conteudos);
        this.carregandoConteudo.set(false);
      },
      error: () => this.carregandoConteudo.set(false),
    });

    this.alunoApi.conteudosConcluidos(materiaId).subscribe({
      next: (ids) => this.concluidos.set(new Set(ids)),
      error: () => this.concluidos.set(new Set()),
    });
  }

  /**
   * Marca/desmarca o conteúdo. O estado local muda antes da resposta para o
   * clique responder na hora; se o servidor recusar, volta ao que era — ficar
   * marcado sem ter gravado seria pior do que a espera.
   */
  alternarConcluido(c: ConteudoMateriaDTO): void {
    const estava = this.estaConcluido(c);
    this.salvandoId.set(c.id);
    this.aplicarLocal(c.id, !estava);

    const chamada = estava
      ? this.alunoApi.desmarcarConteudo(c.id)
      : this.alunoApi.concluirConteudo(c.id);

    chamada.subscribe({
      next: () => this.salvandoId.set(null),
      error: () => {
        this.aplicarLocal(c.id, estava);
        this.salvandoId.set(null);
      },
    });
  }

  private aplicarLocal(conteudoId: number, concluido: boolean): void {
    this.concluidos.update((atual) => {
      const novo = new Set(atual);
      if (concluido) novo.add(conteudoId);
      else novo.delete(conteudoId);
      return novo;
    });
  }

  /** Posição do tópico aberto na lista, ou -1 se não há nenhum aberto. */
  private readonly indiceAtual = computed(() => {
    const atual = this.topicoSelecionado();
    return atual ? this.conteudos().findIndex((c) => c.id === atual.id) : -1;
  });

  readonly topicoAnterior = computed(() => {
    const i = this.indiceAtual();
    return i > 0 ? this.conteudos()[i - 1] : null;
  });

  readonly topicoProximo = computed(() => {
    const i = this.indiceAtual();
    const lista = this.conteudos();
    return i >= 0 && i < lista.length - 1 ? lista[i + 1] : null;
  });

  selecionarTopico(c: ConteudoMateriaDTO): void {
    this.topicoSelecionado.set(c);
  }

  irParaAnterior(): void {
    const anterior = this.topicoAnterior();
    if (anterior) this.abrirDoTopo(anterior);
  }

  irParaProximo(): void {
    const proximo = this.topicoProximo();
    if (proximo) this.abrirDoTopo(proximo);
  }

  voltarTopicos(): void {
    this.topicoSelecionado.set(null);
  }

  /**
   * Troca o tópico e sobe a página. Sem isso o aluno cai no meio do texto
   * seguinte, já que o botão de avançar fica no rodapé do anterior.
   */
  private abrirDoTopo(c: ConteudoMateriaDTO): void {
    this.topicoSelecionado.set(c);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    document.body.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
