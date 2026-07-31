import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SUBJECTS } from '../materias/materias.data';
import { MateriasService } from '../api/materias.service';
import { ConteudoMateriaDTO } from '../core/api.models';

/**
 * Detalhe de uma disciplina. O cabeçalho (progresso, ícone, nível) continua
 * vindo do mock em materias.data.ts — só os "Conteúdos da disciplina" passam a
 * vir do backend quando a matéria correspondente já tem documentos cadastrados
 * (piloto: Matemática); sem conteúdo real, cai de volta nos tópicos estáticos.
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

  /** Vem do parâmetro de rota /disciplina/:id (withComponentInputBinding). */
  readonly id = input<string>('');

  readonly disciplina = computed(() => SUBJECTS.find((s) => s.id === this.id()) ?? null);

  readonly carregandoConteudo = signal(true);
  readonly conteudos = signal<ConteudoMateriaDTO[]>([]);
  readonly topicoSelecionado = signal<ConteudoMateriaDTO | null>(null);

  constructor() {
    // Precisa ser um effect, e não código solto no construtor: `id` é um signal
    // input preenchido pelo withComponentInputBinding depois da construção. Lido
    // no construtor ele ainda é '', disciplina() é null e o carregamento
    // desistia antes de começar — nenhuma matéria chegava a ter conteúdo, e a
    // tela caía sempre na lista estática, que não é clicável.
    // Como effect, também recarrega ao trocar de disciplina sem sair da rota.
    effect(() => {
      const d = this.disciplina();
      this.topicoSelecionado.set(null);
      this.conteudos.set([]);

      if (!d) {
        this.carregandoConteudo.set(false);
        return;
      }
      untracked(() => this.carregarConteudos(d.title));
    });
  }

  /** Resolve o id numérico da Materia pelo título (os dois catálogos usam os mesmos nomes). */
  private carregarConteudos(titulo: string): void {
    this.carregandoConteudo.set(true);
    this.materiasService.listar().subscribe({
      next: (materias) => {
        const materia = materias.find((m) => m.nome.toLowerCase() === titulo.toLowerCase());
        if (!materia) {
          this.carregandoConteudo.set(false);
          return;
        }
        this.materiasService.listarConteudos(materia.id).subscribe({
          next: (conteudos) => {
            this.conteudos.set(conteudos);
            this.carregandoConteudo.set(false);
          },
          error: () => this.carregandoConteudo.set(false),
        });
      },
      error: () => this.carregandoConteudo.set(false),
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
