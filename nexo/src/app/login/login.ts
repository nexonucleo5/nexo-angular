import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
  public mostrarSenha    = false;
  public carregando      = false; // ← estado de loading durante a chamada HTTP
  public mensagemErro    = '';
  public mensagemSucesso = '';

  /** Destino após login por role */
  private readonly destinos: Record<string, string> = {
    aluno:     '/dashboards',
    diretor:   '/diretor-dashboards',
    professor: '/professor-dashboard',
  };

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

    this.mensagemErro  = '';
    this.carregando    = true;
    const { username, password } = this.loginForm.value;

    // authService.login() agora retorna Observable<boolean>
    this.authService.login(username, password).subscribe({
      next: (sucesso) => {
        this.carregando = false;
        if (sucesso) {
          const role    = this.authService.usuarioLogado()?.role ?? 'aluno';
          const destino = this.destinos[role] ?? '/dashboards';
          this.mensagemSucesso = '✅ Login realizado! Redirecionando...';
          setTimeout(() => this.router.navigate([destino]), 150);
        } else {
          this.mensagemErro = '❌ Credenciais inválidas. Tente novamente.';
        }
      },
      error: () => {
        this.carregando   = false;
        this.mensagemErro = '❌ Erro de conexão com o servidor. Tente novamente.';
      },
    });
  }

  /** Navega para a tela de cadastro de aluno */
  public irParaCadastro(): void {
    this.router.navigate(['/cadastro']);
  }
}