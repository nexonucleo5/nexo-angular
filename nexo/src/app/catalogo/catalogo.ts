import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminService } from '../api/admin.service';
import { ConteudoAdminDTO, DesafioAdminDTO, MateriaCatalogoDTO } from '../core/api.models';

type Aba = 'conteudos' | 'desafios';

/**
 * Catálogo de conteúdo: o que está no ar para o aluno, e em que ordem.
 *
 * <p>É a tela que dá sentido ao "sistema focado em aprendizado e retenção" — o
 * administrador tira do ar o que está errado, devolve o corrigido e ordena a
 * sequência em que o assunto é estudado.
 *
 * <p>Não há apagar de propósito: excluir um conteúdo levaria junto o registro de
 * quem já o concluiu, e é justamente esse registro que mede a retenção.
 */
@Component({
  selector: 'app-catalogo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './catalogo.html',
  styleUrl: './catalogo.scss',
})
export class Catalogo {
  private readonly admin = inject(AdminService);
  private readonly route = inject(ActivatedRoute);

  readonly aba = signal<Aba>('conteudos');

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly materias = signal<MateriaCatalogoDTO[]>([]);
  readonly materiaSelecionadaId = signal<number | null>(null);

  readonly materiaSelecionada = computed(() =>
    this.materias().find((m) => m.id === this.materiaSelecionadaId()) ?? null,
  );

  readonly conteudos = signal<ConteudoAdminDTO[]>([]);
  readonly carregandoConteudos = signal(false);

  readonly desafios = signal<DesafioAdminDTO[]>([]);
  readonly carregandoDesafios = signal(false);

  readonly acaoEmCurso = signal<number | null>(null);
  readonly acaoErro = signal<string | null>(null);
  readonly acaoSucesso = signal<string | null>(null);

  /** Vindo do painel (?materia=ID) a tela já abre na matéria que motivou o clique. */
  private readonly materiaPedida = Number(this.route.snapshot.queryParamMap.get('materia')) || null;

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.admin.catalogo().subscribe({
      next: (lista) => {
        this.materias.set(lista);
        this.carregando.set(false);
        const alvo = lista.find((m) => m.id === this.materiaPedida) ?? lista[0];
        if (alvo) this.selecionarMateria(alvo.id);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  selecionarMateria(id: number): void {
    this.materiaSelecionadaId.set(id);
    this.limparFeedback();
    this.carregandoConteudos.set(true);
    this.admin.conteudosDaMateria(id).subscribe({
      next: (lista) => {
        this.conteudos.set(lista);
        this.carregandoConteudos.set(false);
      },
      error: () => {
        this.carregandoConteudos.set(false);
        this.acaoErro.set('Não foi possível carregar os conteúdos desta matéria.');
      },
    });
  }

  abrirAba(aba: Aba): void {
    this.aba.set(aba);
    this.limparFeedback();
    // Os desafios são carregados só quando a aba é aberta, e uma vez só.
    if (aba === 'desafios' && this.desafios().length === 0) this.carregarDesafios();
  }

  private carregarDesafios(): void {
    this.carregandoDesafios.set(true);
    this.admin.desafios().subscribe({
      next: (lista) => {
        this.desafios.set(lista);
        this.carregandoDesafios.set(false);
      },
      error: () => {
        this.carregandoDesafios.set(false);
        this.acaoErro.set('Não foi possível carregar os desafios.');
      },
    });
  }

  // ── Publicação ────────────────────────────────────────────────────────────

  alternarPublicadoConteudo(c: ConteudoAdminDTO): void {
    this.acaoEmCurso.set(c.id);
    this.limparFeedback();
    this.admin.publicarConteudo(c.id, !c.publicado).subscribe({
      next: (atualizado) => {
        this.conteudos.update((lista) => lista.map((x) => (x.id === atualizado.id ? atualizado : x)));
        this.acaoEmCurso.set(null);
        this.acaoSucesso.set(atualizado.publicado
          ? `"${atualizado.titulo}" voltou para a tela do aluno.`
          : `"${atualizado.titulo}" saiu da tela do aluno — o progresso de quem já leu ficou.`);
        this.atualizarContagemDaMateria();
      },
      error: (erro) => {
        this.acaoEmCurso.set(null);
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível alterar o conteúdo.');
      },
    });
  }

  alternarPublicadoDesafio(d: DesafioAdminDTO): void {
    this.acaoEmCurso.set(d.id);
    this.limparFeedback();
    this.admin.publicarDesafio(d.id, !d.publicado).subscribe({
      next: (atualizado) => {
        this.desafios.update((lista) => lista.map((x) => (x.id === atualizado.id ? atualizado : x)));
        this.acaoEmCurso.set(null);
        this.acaoSucesso.set(atualizado.publicado
          ? `"${atualizado.titulo}" voltou ao catálogo do aluno.`
          : `"${atualizado.titulo}" saiu do catálogo do aluno.`);
      },
      error: (erro) => {
        this.acaoEmCurso.set(null);
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível alterar o desafio.');
      },
    });
  }

  /** Mantém o contador da lista de matérias em dia sem recarregar o catálogo inteiro. */
  private atualizarContagemDaMateria(): void {
    const id = this.materiaSelecionadaId();
    if (id == null) return;
    const publicados = this.conteudos().filter((c) => c.publicado).length;
    this.materias.update((lista) =>
      lista.map((m) => (m.id === id ? { ...m, conteudosPublicados: publicados } : m)),
    );
  }

  // ── Ordem ─────────────────────────────────────────────────────────────────

  /**
   * Move um conteúdo uma posição e manda a lista inteira. O servidor recusa lista
   * parcial de propósito: a ordem é do conjunto, e mover um item desloca os outros.
   */
  mover(indice: number, direcao: -1 | 1): void {
    const materiaId = this.materiaSelecionadaId();
    const lista = [...this.conteudos()];
    const destino = indice + direcao;
    if (materiaId == null || destino < 0 || destino >= lista.length) return;

    [lista[indice], lista[destino]] = [lista[destino], lista[indice]];
    // Aplica na tela antes da resposta: a seta precisa parecer instantânea, e um
    // erro devolve a ordem do servidor logo abaixo.
    this.conteudos.set(lista);
    this.limparFeedback();

    this.admin.reordenar(materiaId, lista.map((c) => c.id)).subscribe({
      next: (ordenados) => this.conteudos.set(ordenados),
      error: (erro) => {
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível salvar a nova ordem.');
        this.selecionarMateria(materiaId); // recarrega a ordem que o servidor tem
      },
    });
  }

  private limparFeedback(): void {
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
  }
}
