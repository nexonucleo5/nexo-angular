import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { AlunosService } from '../api/alunos.service';
import { ApiErro } from '../core/api.models';

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

  cadastroForm: FormGroup;
  emailGerado     = '';
  senhaGerada     = '';
  acessoGerado    = false;
  enviando        = false;
  mensagemSucesso = '';
  mensagemErro    = '';

  constructor() {
    this.cadastroForm = this.fb.group({
      nome:                ['', [Validators.required, Validators.minLength(3)]],
      cpf:                 ['', [Validators.required, Validators.minLength(14)]],
      sexo:                ['', Validators.required],
      telefone:            [''],
      dataNascimento:      ['', Validators.required],
      emailResponsavel:    ['', [Validators.required, Validators.email]],
      cpfResponsavel:      ['', Validators.required],
      telefoneResponsavel: [''],
      endereco:            ['', Validators.required],
      complemento:         [''],
    });
  }

  /** Atalho para acessar os controles no template */
  get f() {
    return this.cadastroForm.controls;
  }

  /**
   * Submete o cadastro para a API. O e-mail institucional e a senha provisória
   * são gerados e persistidos no backend — o client não decide credenciais.
   */
  finalizarCadastro(): void {
    if (this.cadastroForm.invalid) {
      this.cadastroForm.markAllAsTouched();
      this.mensagemErro = 'Por favor, preencha todos os campos obrigatórios.';
      return;
    }

    this.enviando = true;
    this.mensagemErro = '';
    this.mensagemSucesso = '';

    this.alunosService.cadastrar(this.cadastroForm.value).subscribe({
      next: (criado) => {
        this.enviando        = false;
        this.emailGerado     = criado.emailInstitucional;
        this.senhaGerada     = criado.senhaProvisoria;
        this.acessoGerado    = true;
        this.mensagemSucesso = `✅ Aluno cadastrado! Login: ${criado.emailInstitucional}`;
        this.cadastroForm.reset();
      },
      error: (erro: ApiErro) => {
        this.enviando = false;
        const detalhes = erro.fields ? ' ' + Object.values(erro.fields).join(' ') : '';
        this.mensagemErro = `❌ ${erro.message}${detalhes}`;
      },
    });
  }

  // ── Máscaras ────────────────────────────────────────────────────────

  aplicarMascaraCpf(event: Event, campo: string): void {
    const input = event.target as HTMLInputElement;
    let v = input.value.replace(/\D/g, '').slice(0, 11);
    v = v.replace(/(\d{3})(\d)/, '$1.$2');
    v = v.replace(/(\d{3})(\d)/, '$1.$2');
    v = v.replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    this.cadastroForm.get(campo)?.setValue(v, { emitEvent: false });
  }

  aplicarMascaraTelefone(event: Event, campo: string): void {
    const input = event.target as HTMLInputElement;
    let v = input.value.replace(/\D/g, '').slice(0, 11);
    v = v.replace(/(\d{2})(\d)/, '($1) $2');
    v = v.replace(/(\d{5})(\d)/, '$1-$2');
    this.cadastroForm.get(campo)?.setValue(v, { emitEvent: false });
  }
}
