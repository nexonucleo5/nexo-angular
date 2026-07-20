import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { GestaoDiretorService } from '../api/gestao-diretor.service';
import { AlunoRiscoDTO, RiscoEvasao } from '../core/api.models';
import { exportarCsv } from '../core/csv.util';

/** Aluno em risco já no formato de exibição da tela (derivado do DTO agregado do backend). */
interface AlunoRiscoView {
  nome: string;
  matricula: string;
  turma: string;
  frequencia: number;
  participacao: number;
  notaMedia: number;
  ultimoAcesso: string;
  risco: 'Alto' | 'Médio' | 'Baixo';
  motivoPrincipal: string;
  intervencoes: string;
  tempoIntervencao: string;
  contatoResponsavel: string;
  emailResponsavel: string;
  foto: string;
}

const FOTO_PADRAO = 'assets/imagensProjeto/gabrielZapelini.png';

const RISCO_LABEL: Record<RiscoEvasao, 'Alto' | 'Médio' | 'Baixo'> = {
  ALTO: 'Alto',
  MEDIO: 'Médio',
  BAIXO: 'Baixo',
};

@Component({
  selector: 'app-gestao-evasao',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-evasao.html',
  styleUrl: './gestao-evasao.scss',
})
export class GestaoEvasao {
  private readonly gestao = inject(GestaoDiretorService);

  readonly buscaNome = signal('');
  readonly riscoSelecionado = signal('Todos os Riscos');
  readonly turmaSelecionada = signal('Todas as Turmas');

  readonly carregando = signal(true);
  readonly erro = signal(false);
  private readonly alunos = signal<AlunoRiscoView[]>([]);
  readonly detalhe = signal<AlunoRiscoView | null>(null);

  readonly riscos = ['Todos os Riscos', 'Risco Alto', 'Risco Médio', 'Risco Baixo'];

  /** Turmas reais derivadas dos dados carregados (antes eram fixas e não batiam com o backend). */
  readonly turmas = computed(() => {
    const nomes = Array.from(new Set(this.alunos().map((a) => a.turma))).sort();
    return ['Todas as Turmas', ...nomes];
  });

  readonly alunosFiltrados = computed(() => {
    const termo = this.normalizar(this.buscaNome());
    const risco = this.riscoSelecionado();
    const turma = this.turmaSelecionada();

    return this.alunos().filter((aluno) => {
      const condicaoBusca =
        !termo ||
        this.normalizar(aluno.nome).includes(termo) ||
        aluno.matricula.includes(termo) ||
        this.normalizar(aluno.turma).includes(termo);

      const condicaoRisco =
        risco === 'Todos os Riscos' || this.normalizar(risco).includes(this.normalizar(aluno.risco));

      const condicaoTurma = turma === 'Todas as Turmas' || aluno.turma === turma;

      return condicaoBusca && condicaoRisco && condicaoTurma;
    });
  });

  // KPIs derivados da lista real (antes eram números fixos no template)
  readonly totalEmRisco = computed(() => this.alunos().filter((a) => a.risco !== 'Baixo').length);
  readonly totalAlto = computed(() => this.alunos().filter((a) => a.risco === 'Alto').length);
  readonly totalMedio = computed(() => this.alunos().filter((a) => a.risco === 'Médio').length);
  readonly totalBaixo = computed(() => this.alunos().filter((a) => a.risco === 'Baixo').length);

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.gestao.riscoEvasao().subscribe({
      next: (lista) => {
        this.alunos.set(lista.map((dto) => this.paraView(dto)));
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  private paraView(dto: AlunoRiscoDTO): AlunoRiscoView {
    return {
      nome: dto.nome,
      matricula: dto.matricula,
      turma: dto.turma ?? 'Sem turma',
      frequencia: Math.max(0, Math.round(100 - dto.percentualFaltas)),
      participacao: dto.engajamento,
      notaMedia: dto.media,
      ultimoAcesso: this.formatarRelativo(dto.ultimoAcessoEm),
      risco: RISCO_LABEL[dto.risco],
      motivoPrincipal: dto.motivoPrincipal,
      intervencoes: `${dto.intervencoes} intervenção(ões)`,
      tempoIntervencao: dto.ultimaIntervencaoEm ? this.formatarRelativo(dto.ultimaIntervencaoEm) : 'Nenhuma ainda',
      contatoResponsavel: dto.telefoneResponsavel ?? 'Não informado',
      emailResponsavel: dto.emailResponsavel ?? '',
      foto: dto.foto || FOTO_PADRAO,
    };
  }

  /** Formata um ISO em texto relativo ("há X dias"), com a formatação no client (padrão do projeto). */
  private formatarRelativo(iso: string | null): string {
    if (!iso) return '—';
    const dias = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
    if (dias <= 0) return 'Hoje';
    if (dias === 1) return 'Ontem';
    if (dias < 7) return `Há ${dias} dias`;
    if (dias < 14) return 'Há 1 semana';
    if (dias < 30) return `Há ${Math.floor(dias / 7)} semanas`;
    return `Há ${Math.floor(dias / 30)} mês(es)`;
  }

  private normalizar(s: string): string {
    return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  getClasseRisco(risco: string): string {
    return this.normalizar(risco); // 'alto' | 'medio' | 'baixo'
  }

  /** Exporta a lista filtrada de alunos em risco para CSV. */
  exportar(): void {
    const linhas = this.alunosFiltrados().map((a) => [
      a.nome, a.matricula, a.turma, a.risco, `${a.frequencia}%`, `${a.participacao}%`,
      a.notaMedia, a.motivoPrincipal, a.contatoResponsavel, a.emailResponsavel,
    ]);
    exportarCsv('alunos-em-risco', [
      'Nome', 'Matrícula', 'Turma', 'Risco', 'Frequência', 'Participação',
      'Nota Média', 'Motivo Principal', 'Telefone Responsável', 'E-mail Responsável',
    ], linhas);
  }

  verDetalhes(aluno: AlunoRiscoView): void {
    this.detalhe.set(aluno);
  }

  fecharDetalhes(): void {
    this.detalhe.set(null);
  }

  /** Abre o cliente de e-mail para contatar o responsável do aluno. */
  contatar(aluno: AlunoRiscoView): void {
    if (aluno.emailResponsavel) {
      const assunto = encodeURIComponent(`Acompanhamento — ${aluno.nome}`);
      window.location.href = `mailto:${aluno.emailResponsavel}?subject=${assunto}`;
    }
  }
}
