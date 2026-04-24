import { Component, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  // Injeção de dependências
  private fb = inject(FormBuilder);
  private router = inject(Router);

  // Estados reativos da tela
  public loginForm: FormGroup;
  public mostrarSenha = false;
  public mensagemErro = '';
  public mensagemSucesso = '';

  constructor() {
    // Inicializa a estrutura do formulário com validações obrigatórias
    this.loginForm = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  /** Ação disparada ao submeter o formulário */
  public realizarLogin(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      this.mensagemErro = 'Preencha o login e a senha para continuar.';
      return;
    }

    this.mensagemErro = '';
    const { username, password } = this.loginForm.value;

    // Prontinho para integrar com o seu serviço de API / Spring Security futuramente
    console.log('Enviando dados para o servidor:', { username, password });

    this.mensagemSucesso = '✅ Login realizado com sucesso! Redirecionando...';

    // Redireciona para a rota '/dashboard'
    setTimeout(() => {
      this.router.navigate(['/dashboard']);
    }, 120);
  }
}