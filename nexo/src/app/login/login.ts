import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';
import { ApiErro } from '../core/api.models';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, RouterLink],
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

  constructor() {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
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
      next: () => {
        this.carregando = false;
        this.mensagemSucesso = '✅ Login realizado com sucesso! Redirecionando...';
        setTimeout(() => this.router.navigate(['/dashboards']), 120);
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
