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
 * Cadastros do diretor: aluno e professor em abas da mesma tela. As credenciais
 * são sempre geradas no backend — o client apenas exibe o que voltou.
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

  readonly enviando = signal(false);
  readonly acesso = signal<AcessoGerado | null>(null);
  readonly mensagemSucesso = signal('');
  readonly mensagemErro = signal('');

  alunoForm: FormGroup;
  professorForm: FormGroup;

  constructor() {
    this.alunoForm = this.fb.group({
      nome: ['', [Validators.required, Validators.minLength(3)]],
      dataNascimento: ['', Validators.required],
      sexo: ['', Validators.required],
      turmaId: [null, Validators.required],
      // Endereço é opcional (a escola matricula antes de ter a documentação toda),
      // então nenhum campo daqui tem Validators.required — o que impediria o envio.
      endereco: this.fb.group({
        cep: [''],
        logradouro: [''],
        numero: [''],
        complemento: [''],
        bairro: [''],
        cidade: [''],
        uf: [''],
      }),
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

  get enderecoForm(): FormGroup {
    return this.alunoForm.get('endereco') as FormGroup;
  }

  // ── CEP ─────────────────────────────────────────────────────────────

  readonly buscandoCep = signal(false);
  readonly cepErro = signal('');
  readonly cepPreenchido = signal(false);

  /**
   * Busca o CEP e preenche logradouro/bairro/cidade/UF. Chamado quando o campo
   * perde o foco e ao pressionar Enter — não a cada tecla: seriam 8 requisições
   * para um CEP digitado, e as 7 primeiras a serviço público por CEP incompleto.
   *
   * <p>Falha não trava o cadastro: os campos continuam editáveis à mão, que é o
   * motivo de o endereço inteiro ser opcional.
   */
  buscarCep(): void {
    const cep = (this.enderecoForm.value.cep ?? '').replace(/\D/g, '');
    this.cepErro.set('');

    if (!cep) return;
    if (cep.length !== 8) {
      this.cepErro.set('O CEP tem 8 dígitos.');
      return;
    }

    this.buscandoCep.set(true);
    this.alunosService.buscarCep(cep).subscribe({
      next: (e) => {
        this.buscandoCep.set(false);
        this.cepPreenchido.set(true);
        // patchValue e não setValue: número e complemento são de quem digita, e
        // um setValue no grupo apagaria o que já foi preenchido neles.
        this.enderecoForm.patchValue({
          logradouro: e.logradouro ?? '',
          bairro: e.bairro ?? '',
          cidade: e.cidade ?? '',
          uf: e.uf ?? '',
        });
      },
      error: (erro: ApiErro) => {
        this.buscandoCep.set(false);
        this.cepPreenchido.set(false);
        this.cepErro.set(
          erro.status === 404
            ? 'CEP não encontrado. Confira o número ou preencha à mão.'
            : erro.message || 'Não foi possível consultar o CEP agora.',
        );
      },
    });
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

  alternarMateria(id: number): void {
    const atuais = this.materiaIdsSelecionadas;
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
        this.alunoForm.reset({
          nome: '', dataNascimento: '', sexo: '', turmaId: null,
          endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
        });
        this.cepPreenchido.set(false);
        this.cepErro.set('');
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
  // A senha provisória aparece uma única vez; sem isto a secretária anotava no
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
