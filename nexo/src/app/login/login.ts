import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';
import { ApiErro } from '../core/api.models';
import { aplicarTema, temaSalvoEscuro } from '../configuracoes/settings-store';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private fb          = inject(FormBuilder);
  private router      = inject(Router);
  private authService = inject(AuthService);

  public loginForm: FormGroup;
  public mostrarSenha    = false;
  public carregando      = false;
  public mensagemErro    = '';
  public mensagemSucesso = '';

  /** Tema antes do login: mesma chave de cache que o App e o SettingsStore usam. */
  public temaEscuro = signal(temaSalvoEscuro());

  constructor() {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }

  public alternarTema(): void {
    this.temaEscuro.update((escuro) => !escuro);
    aplicarTema(this.temaEscuro());
  }

  public realizarLogin(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.mensagemErro = 'Preencha o login e a senha para continuar.';
      return;
    }

    this.mensagemErro = '';
    this.carregando = true;
    const { username, password } = this.loginForm.value;

    this.authService.login(username, password).subscribe({
      next: (resposta) => {
        this.carregando = false;
        // Senha provisória: o primeiro passo é trocá-la, antes de qualquer outra tela.
        const destino = resposta.usuario.precisaTrocarSenha ? '/trocar-senha' : '/dashboards';
        this.mensagemSucesso = resposta.usuario.precisaTrocarSenha
          ? '✅ Login realizado! Defina uma senha nova para continuar...'
          : '✅ Login realizado com sucesso! Redirecionando...';
        setTimeout(() => this.router.navigate([destino]), 120);
      },
      error: (erro: ApiErro) => {
        this.carregando = false;
        this.mensagemErro = erro.status === 401
          ? '❌ Credenciais inválidas. Tente novamente.'
          : `❌ ${erro.message}`;
      },
    });
  }
}
