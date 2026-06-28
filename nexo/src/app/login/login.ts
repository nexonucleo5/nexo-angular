import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';

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
  public mostrarSenha   = false;
  public mensagemErro   = '';
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
    const { username, password } = this.loginForm.value;
    const sucesso = this.authService.login(username, password);

    if (sucesso) {
      this.mensagemSucesso = '✅ Login realizado com sucesso! Redirecionando...';
      setTimeout(() => this.router.navigate(['/dashboards']), 120);
    } else {
      this.mensagemErro = '❌ Credenciais inválidas. Tente novamente.';
    }
  }
}