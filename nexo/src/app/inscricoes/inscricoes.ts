import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { InscricoesService } from '../api/inscricoes.service';
import { TurmasService } from '../api/turmas.service';
import { InscricaoDTO, TurmaDTO } from '../core/api.models';
import { exportarCsv } from '../core/csv.util';

interface InscricaoView {
  id: number;
  alunoId: number;
  nome: string;
  iniciais: string;
  turma: string;
  turmaId: number | null;
  ativo: boolean;
  criadaEm: string;
}

/**
 * Inscrições dos alunos nas turmas de estudo.
 *
 * <p>Foi a tela de matrículas, e encolheu de propósito. Saíram as abas de
 * documentos e prontuário, a rematrícula individual e em lote, a emissão de
 * declaração e de histórico escolar, e os estados de trancamento e cancelamento.
 * Tudo isso é vida escolar, e vive no sistema de aula da escola.
 *
 * <p>O que ficou responde à única pergunta que o aprendizado faz: <b>de qual turma
 * este aluno vê o conteúdo</b> — e se ele ainda o vê.
 */
@Component({
  selector: 'app-inscricoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inscricoes.html',
  styleUrl: './inscricoes.scss',
})
export class Inscricoes {
  private readonly api = inject(InscricoesService);
  private readonly turmasApi = inject(TurmasService);
  private readonly route = inject(ActivatedRoute);

  /** Turmas para o seletor de transferência — carregadas uma vez, junto da lista. */
  readonly turmas = signal<TurmaDTO[]>([]);

  readonly buscaTermo = signal('');
  readonly situacaoSelecionada = signal('Todas');
  readonly situacaoOpcoes = ['Todas', 'Ativas', 'Inativas'];

  readonly turmaSelecionada = signal('Todas as Turmas');
  /** Opções vindas das próprias linhas: cobre "Sem turma" e só lista o que existe. */
  readonly turmaOpcoes = computed(() => {
    const nomes = new Set(this.inscricoes().map((i) => i.turma));
    return ['Todas as Turmas', ...[...nomes].sort()];
  });

  /** Contexto vindo do painel (?turma=ID chega já filtrado). */
  private pendenteTurmaId: number | null = null;

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly inscricoes = signal<InscricaoView[]>([]);
  readonly detalhe = signal<InscricaoView | null>(null);

  readonly inscricoesFiltradas = computed(() => {
    const termo = this.buscaTermo().toLowerCase();
    const situacao = this.situacaoSelecionada();
    const turma = this.turmaSelecionada();
    return this.inscricoes().filter((i) => {
      const buscaOk = !termo || i.nome.toLowerCase().includes(termo);
      const situacaoOk = situacao === 'Todas'
        || (situacao === 'Ativas' ? i.ativo : !i.ativo);
      const turmaOk = turma === 'Todas as Turmas' || i.turma === turma;
      return buscaOk && situacaoOk && turmaOk;
    });
  });

  readonly total = computed(() => this.inscricoes().length);
  readonly ativas = computed(() => this.inscricoes().filter((i) => i.ativo).length);
  readonly inativas = computed(() => this.inscricoes().filter((i) => !i.ativo).length);

  constructor() {
    this.pendenteTurmaId = Number(this.route.snapshot.queryParamMap.get('turma')) || null;

    this.carregar();
    this.turmasApi.listar().subscribe({
      next: (turmas) => {
        this.turmas.set(turmas);
        // ?turma= chega como id (é o que o painel conhece); o filtro é por nome.
        if (this.pendenteTurmaId != null) {
          const nome = turmas.find((t) => t.id === this.pendenteTurmaId)?.nome;
          if (nome) this.turmaSelecionada.set(nome);
          this.pendenteTurmaId = null;
        }
      },
      error: () => this.turmas.set([]), // sem turmas o seletor de transferência só não aparece
    });
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.api.listar({ size: 100 }).subscribe({
      next: (page) => {
        this.inscricoes.set(page.content.map((i) => this.paraView(i)));
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  private paraView(i: InscricaoDTO): InscricaoView {
    return {
      id: i.id,
      alunoId: i.alunoId,
      nome: i.aluno,
      iniciais: this.iniciais(i.aluno),
      turma: i.turma ?? 'Sem turma',
      turmaId: i.turmaId,
      ativo: i.ativo,
      criadaEm: this.formatarData(i.criadaEm),
    };
  }

  verDetalhes(i: InscricaoView): void {
    this.detalhe.set(i);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
  }

  // ── Ações ─────────────────────────────────────────────────────────────────

  readonly acaoEmCurso = signal(false);
  readonly acaoErro = signal<string | null>(null);
  /** Confirmação visível: sem ela, salvar em silêncio parece travado. */
  readonly acaoSucesso = signal<string | null>(null);

  alternarAtivo(i: InscricaoView): void {
    this.executar(this.api.atualizarAtivo(i.id, !i.ativo),
      i.ativo
        ? 'Inscrição desativada — o aluno deixa de ver o conteúdo da turma.'
        : 'Inscrição reativada.');
  }

  transferirTurma(i: InscricaoView, turmaId: string): void {
    const id = Number(turmaId);
    if (!id) return;
    this.executar(this.api.transferirTurma(i.id, id), 'Aluno transferido de turma.');
  }

  /** Aplica a resposta do servidor na lista e no modal — sem recarregar a página toda. */
  private executar(chamada: Observable<InscricaoDTO>, sucesso: string): void {
    this.acaoEmCurso.set(true);
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
    chamada.subscribe({
      next: (dto) => {
        const atualizada = this.paraView(dto);
        this.inscricoes.update((lista) => lista.map((x) => (x.id === dto.id ? atualizada : x)));
        this.detalhe.set(atualizada);
        this.acaoEmCurso.set(false);
        this.acaoSucesso.set(sucesso);
      },
      error: (erro) => {
        this.acaoErro.set(
          erro?.error?.fields?.turmaId ?? erro?.error?.message ?? 'Não foi possível concluir a ação.',
        );
        this.acaoEmCurso.set(false);
      },
    });
  }

  private iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[partes.length - 1]?.[0] ?? '')).toUpperCase();
  }

  private formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('pt-BR');
  }

  /** Exporta a lista filtrada para CSV — só nome, turma e situação. */
  exportar(): void {
    const linhas = this.inscricoesFiltradas().map((i) => [
      i.nome, i.turma, i.ativo ? 'Ativa' : 'Inativa', i.criadaEm,
    ]);
    exportarCsv('inscricoes', ['Aluno', 'Turma', 'Situação', 'Desde'], linhas);
  }
}
