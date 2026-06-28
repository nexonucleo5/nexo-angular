import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';

interface DadosCadastro {
  nome: string;
  cpf: string;
  sexo: string;
  telefone: string;
  dataNascimento: string;
  emailResponsavel: string;
  cpfResponsavel: string;
  telefoneResponsavel: string;
  endereco: string;
  complemento: string;
  emailGerado: string;
  senhaGerada: string;
}

@Component({
  selector: 'app-cadastro',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './cadastro.html',
  styleUrl: './cadastro.scss',
})
export class Cadastro {
  private fb = inject(FormBuilder);

  cadastroForm: FormGroup;
  emailGerado    = '';
  senhaGerada    = '';
  acessoGerado   = false;
  mensagemSucesso = '';
  mensagemErro   = '';

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

  /** Gera e-mail e senha provisória a partir do nome e CPF */
  gerarAcesso(): void {
    const nome = this.cadastroForm.get('nome')?.value as string;
    const cpf  = this.cadastroForm.get('cpf')?.value  as string;

    if (!nome?.trim() || !cpf?.trim()) {
      this.mensagemErro = 'Preencha o Nome e o CPF antes de gerar o acesso.';
      return;
    }

    const primeiroNome = nome.trim().split(' ')[0].toLowerCase();
    const cpfNumeros   = cpf.replace(/\D/g, '');
    const sufixoCpf    = cpfNumeros.slice(-4);

    this.emailGerado  = `${primeiroNome}.${sufixoCpf}@nexo.escola.com`;
    this.senhaGerada  = `Nexo@${sufixoCpf}`;
    this.acessoGerado = true;
    this.mensagemErro = '';
  }

  /** Submete o formulário */
  finalizarCadastro(): void {
    if (this.cadastroForm.invalid) {
      this.cadastroForm.markAllAsTouched();
      this.mensagemErro = 'Por favor, preencha todos os campos obrigatórios.';
      return;
    }

    if (!this.acessoGerado) {
      this.mensagemErro = 'Clique em "Gerar Acesso" antes de finalizar o cadastro.';
      return;
    }

    const dados: DadosCadastro = {
      ...this.cadastroForm.value,
      emailGerado: this.emailGerado,
      senhaGerada: this.senhaGerada,
    };

    // TODO: substituir por chamada real à API (ex: this.alunoService.cadastrar(dados))
    console.log('Dados prontos para envio ao backend:', dados);

    this.mensagemSucesso = `✅ Aluno cadastrado! Login: ${this.emailGerado}`;
    this.mensagemErro    = '';
    this.cadastroForm.reset();
    this.acessoGerado    = false;
    this.emailGerado     = '';
    this.senhaGerada     = '';
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