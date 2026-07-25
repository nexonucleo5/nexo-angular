import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TurmasService } from '../api/turmas.service';
import { PresencaAluno, TurmaDTO } from '../core/api.models';
import { exportarCsv } from '../core/csv.util';
import { AVATAR_PADRAO } from '../core/avatar';

interface AlunoFreqView {
  alunoId: number;
  nome: string;
  matricula: string;
  presente: boolean | null;
  foto: string;
}

interface HistoricoView {
  titulo: string;
  data: string;
  descricao: string;
}


@Component({
  selector: 'app-diario-classe-professor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './diario-classe-professor.html',
  styleUrl: './diario-classe-professor.scss',
})
export class DiarioClasseProfessor implements OnInit {
  private readonly turmasApi = inject(TurmasService);

  readonly turmas = signal<TurmaDTO[]>([]);
  turmaSelecionadaId: number | null = null;
  disciplinaSelecionada = 'História';
  disciplinas = ['História', 'Matemática', 'Português'];
  dataAula = new Date().toISOString().substring(0, 10);
  horarioAula = '14:00';

  tituloAula = '';
  descricaoAula = '';
  observacoesAula = '';

  mensagemSucesso = '';
  mensagemErro = '';
  readonly salvando = signal(false);

  readonly alunos = signal<AlunoFreqView[]>([]);
  readonly historico = signal<HistoricoView[]>([]);

  readonly stats = computed(() => {
    const lista = this.alunos();
    const total = lista.length;
    const presentes = lista.filter((a) => a.presente === true).length;
    const ausentes = lista.filter((a) => a.presente === false).length;
    const taxa = total > 0 ? Math.round((presentes / total) * 100) : 0;
    return [
      { label: 'Total de Alunos', value: `${total}` },
      { label: 'Presentes Hoje', value: `${presentes}` },
      { label: 'Ausentes Hoje', value: `${ausentes}` },
      { label: 'Taxa de Frequência', value: `${taxa}%` },
    ];
  });

  ngOnInit(): void {
    this.turmasApi.listar().subscribe({
      next: (turmas) => {
        this.turmas.set(turmas);
        if (turmas.length) {
          this.turmaSelecionadaId = turmas[0].id;
          this.atualizarDados();
        }
      },
      error: () => (this.mensagemErro = 'Não foi possível carregar as turmas.'),
    });
  }

  atualizarDados(): void {
    const turmaId = this.turmaSelecionadaId;
    if (turmaId == null) return;

    this.turmasApi.frequenciaDoDia(turmaId, this.dataAula).subscribe({
      next: (presencas) => this.alunos.set(presencas.map((p) => this.paraView(p))),
      error: () => (this.mensagemErro = 'Não foi possível carregar a frequência.'),
    });

    this.turmasApi.historicoConteudos(turmaId).subscribe({
      next: (conteudos) =>
        this.historico.set(
          conteudos.map((c) => ({
            titulo: c.titulo,
            data: this.formatarData(c.data),
            descricao: c.descricao,
          })),
        ),
      error: () => this.historico.set([]),
    });
  }

  private paraView(p: PresencaAluno): AlunoFreqView {
    return {
      alunoId: p.alunoId,
      nome: p.nome,
      matricula: `2024${String(p.alunoId).padStart(3, '0')}`,
      // Sem registro do dia (presente null) assume presente por padrão para a chamada
      presente: p.presente ?? true,
      foto: AVATAR_PADRAO,
    };
  }

  marcarPresenca(aluno: AlunoFreqView, presente: boolean): void {
    this.alunos.update((lista) =>
      lista.map((a) => (a.alunoId === aluno.alunoId ? { ...a, presente } : a)),
    );
  }

  marcarTodosPresentes(): void {
    this.alunos.update((lista) => lista.map((a) => ({ ...a, presente: true })));
  }

  salvarFrequencia(): void {
    const turmaId = this.turmaSelecionadaId;
    if (turmaId == null || this.salvando()) return;

    this.salvando.set(true);
    const presencas = this.alunos().map((a) => ({ alunoId: a.alunoId, presente: a.presente === true }));
    this.turmasApi.salvarFrequencia(turmaId, this.dataAula, presencas).subscribe({
      next: (resumo) => {
        this.salvando.set(false);
        this._exibirSucesso(`✅ Frequência salva! ${resumo.presentes} presentes, ${resumo.ausentes} ausentes.`);
      },
      error: () => {
        this.salvando.set(false);
        this.mensagemErro = 'Falha ao salvar a frequência.';
      },
    });
  }

  registrarConteudo(): void {
    const turmaId = this.turmaSelecionadaId;
    if (turmaId == null) return;
    if (!this.tituloAula.trim()) {
      this.mensagemErro = 'O título da aula é obrigatório.';
      return;
    }
    if (!this.descricaoAula.trim()) {
      this.mensagemErro = 'Descreva o conteúdo ministrado.';
      return;
    }

    this.turmasApi
      .registrarConteudo(turmaId, {
        titulo: this.tituloAula.trim(),
        descricao: this.descricaoAula.trim(),
        observacoes: this.observacoesAula.trim(),
        data: this.dataAula,
      })
      .subscribe({
        next: (conteudo) => {
          this.historico.update((h) => [
            { titulo: conteudo.titulo, data: this.formatarData(conteudo.data), descricao: conteudo.descricao },
            ...h,
          ]);
          this.tituloAula = '';
          this.descricaoAula = '';
          this.observacoesAula = '';
          this._exibirSucesso('✅ Conteúdo registrado e adicionado ao histórico!');
        },
        error: () => (this.mensagemErro = 'Falha ao registrar o conteúdo.'),
      });
  }

  private formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  /** Exporta a chamada do dia (frequência) para CSV. */
  exportarDiario(): void {
    const turma = this.turmas().find((t) => t.id === this.turmaSelecionadaId);
    const linhas = this.alunos().map((a) => [
      a.nome, a.matricula, a.presente === true ? 'Presente' : 'Ausente',
    ]);
    exportarCsv(`diario-${turma?.nome ?? 'turma'}-${this.dataAula}`, ['Aluno', 'Matrícula', 'Presença'], linhas);
  }

  private _exibirSucesso(msg: string): void {
    this.mensagemErro = '';
    this.mensagemSucesso = msg;
    setTimeout(() => (this.mensagemSucesso = ''), 3500);
  }
}
