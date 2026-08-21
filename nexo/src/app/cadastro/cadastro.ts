import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AlunosService } from '../api/alunos.service';
import { MateriasService } from '../api/materias.service';
import { ProfessoresService } from '../api/professores.service';
import { TurmasService } from '../api/turmas.service';
import { ApiErro, MateriaDTO, TurmaDTO } from '../core/api.models';

type Aba = 'aluno' | 'professor';

/** Acesso recém-gerado, exibido uma única vez ao diretor. */
interface AcessoGerado {
  login: string;
  senha: string;
}

/**
 * Cadastros: aluno e professor em abas da mesma tela. As credenciais são sempre
 * geradas no backend — o client apenas exibe o que voltou.
 *
 * <p>O formulário do aluno pede nome e turma, e nada mais. Nascimento, sexo e
 * endereço saíram junto com a busca de CEP: este sistema cuida de aprendizado e
 * retenção de conteúdo, e a ficha pessoal do aluno vive no sistema de aula da
 * escola. O do professor mantém os campos porque a validação de idade mínima
 * para lecionar continua sendo regra do servidor.
 */
@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cadastro.html',
  styleUrl: './cadastro.scss',
})
export class Cadastro {
  private fb = inject(FormBuilder);
  private alunosService = inject(AlunosService);
  private professoresService = inject(ProfessoresService);
  private materiasService = inject(MateriasService);
  private turmasService = inject(TurmasService);

  readonly abaAtiva = signal<Aba>('aluno');
  readonly turmas = signal<TurmaDTO[]>([]);
  readonly materias = signal<MateriaDTO[]>([]);

  /**
   * Limites do seletor de data — espelho da regra do servidor (ProfessorService).
   * O navegador barrando já no calendário evita a viagem de ida e volta só para
   * descobrir que 2205 não era 2005; quem decide continua sendo o backend.
   */
  readonly limitesProfessor = Cadastro.faixaDeDatas(18, 100);

  /** Teto de matérias por docente, igual ao do ProfessorService. */
  readonly maximoMaterias = 3;

  private static faixaDeDatas(idadeMinima: number, idadeMaxima: number): { min: string; max: string } {
    const hoje = new Date();
    const iso = (d: Date) => d.toISOString().slice(0, 10);
    return {
      min: iso(new Date(hoje.getFullYear() - idadeMaxima, hoje.getMonth(), hoje.getDate())),
      max: iso(new Date(hoje.getFullYear() - idadeMinima, hoje.getMonth(), hoje.getDate())),
    };
  }

  readonly enviando = signal(false);
  readonly acesso = signal<AcessoGerado | null>(null);
  readonly mensagemSucesso = signal('');
  readonly mensagemErro = signal('');

  alunoForm: FormGroup;
  professorForm: FormGroup;

  constructor() {
    this.alunoForm = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(3)]],
      turmaId: [null, Validators.required],
    });

    this.professorForm = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(3)]],
      dataNascimento: ['', Validators.required],
      sexo: ['', Validators.required],
      materiaIds: [[] as number[], Validators.required],
    });

    this.turmasService.listar().subscribe({ next: (t) => this.turmas.set(t) });
    this.materiasService.listar().subscribe({ next: (m) => this.materias.set(m) });
  }

  setAba(aba: Aba): void {
    if (aba === this.abaAtiva()) return;
    this.abaAtiva.set(aba);
    this.limparFeedback();
  }

  get fa() {
    return this.alunoForm.controls;
  }

  get fp() {
    return this.professorForm.controls;
  }

  // ── Matérias (seleção múltipla) ─────────────────────────────────────

  get materiaIdsSelecionadas(): number[] {
    return this.professorForm.value.materiaIds ?? [];
  }

  materiaSelecionada(id: number): boolean {
    return this.materiaIdsSelecionadas.includes(id);
  }

  /** Já no limite e não marcada: o clique não teria efeito, então some do alcance. */
  materiaBloqueada(id: number): boolean {
    return !this.materiaSelecionada(id) && this.materiaIdsSelecionadas.length >= this.maximoMaterias;
  }

  alternarMateria(id: number): void {
    const atuais = this.materiaIdsSelecionadas;
    if (this.materiaBloqueada(id)) return;
    const novas = atuais.includes(id) ? atuais.filter((m) => m !== id) : [...atuais, id];
    // `required` num array considera [] preenchido; o setErrors garante a validação.
    this.fp['materiaIds'].setValue(novas);
    this.fp['materiaIds'].markAsTouched();
    this.fp['materiaIds'].setErrors(novas.length ? null : { required: true });
  }

  // ── Submissões ──────────────────────────────────────────────────────

  cadastrarAluno(): void {
    if (!this.validar(this.alunoForm)) return;

    this.enviando.set(true);
    this.alunosService.cadastrar(this.alunoForm.value).subscribe({
      next: (criado) => {
        this.enviando.set(false);
        this.acesso.set({ login: criado.emailInstitucional, senha: criado.senhaProvisoria });
        this.mensagemSucesso.set(`Aluno ${criado.nome} cadastrado com sucesso.`);
        this.alunoForm.reset({ nome: '', turmaId: null });
      },
      error: (erro: ApiErro) => this.falhar(erro),
    });
  }

  cadastrarProfessor(): void {
    if (!this.validar(this.professorForm)) return;

    this.enviando.set(true);
    this.professoresService.cadastrar(this.professorForm.value).subscribe({
      next: (criado) => {
        this.enviando.set(false);
        this.acesso.set({ login: criado.emailInstitucional, senha: criado.senhaProvisoria });
        this.mensagemSucesso.set(`Professor ${criado.nome} cadastrado em ${criado.disciplinas}.`);
        this.professorForm.reset({ nome: '', dataNascimento: '', sexo: '', materiaIds: [] });
      },
      error: (erro: ApiErro) => this.falhar(erro),
    });
  }

  private validar(form: FormGroup): boolean {
    this.limparFeedback();
    if (form.invalid) {
      form.markAllAsTouched();
      this.mensagemErro.set('Preencha todos os campos obrigatórios.');
      return false;
    }
    return true;
  }

  private falhar(erro: ApiErro): void {
    this.enviando.set(false);
    const detalhes = erro.fields ? ' ' + Object.values(erro.fields).join(' ') : '';
    this.mensagemErro.set(`${erro.message}${detalhes}`);
  }

  private limparFeedback(): void {
    this.mensagemSucesso.set('');
    this.mensagemErro.set('');
    this.acesso.set(null);
  }

  // ── Copiar credenciais ──────────────────────────────────────────────
  // A senha provisória aparece uma única vez; sem isto quem cadastra anotava no
  // papel (e errava um caractere) para repassar ao aluno.

  readonly copiado = signal(false);
  private timerCopiado: ReturnType<typeof setTimeout> | null = null;

  copiarAcesso(): void {
    const a = this.acesso();
    if (!a) return;
    navigator.clipboard.writeText(`Login: ${a.login}\nSenha provisória: ${a.senha}`).then(() => {
      this.copiado.set(true);
      if (this.timerCopiado) clearTimeout(this.timerCopiado);
      this.timerCopiado = setTimeout(() => this.copiado.set(false), 2500);
    });
  }
}
