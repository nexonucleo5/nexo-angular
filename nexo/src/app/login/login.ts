import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';
import { ApiErro } from '../core/api.models';
import { aplicarTema, temaSalvoEscuro } from '../configuracoes/settings-store';

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
  private route       = inject(ActivatedRoute);

  /**
   * A mesma tela serve às três etapas (entrar, pedir o link, definir a senha nova): o visual
   * é idêntico e assim o SCSS não é duplicado. Quem escolhe é o {@code data.modo} da rota.
   */
  public modo: 'login' | 'esqueci' | 'redefinir' = this.route.snapshot.data['modo'] ?? 'login';

  public esqueciForm: FormGroup;
  public redefinirForm: FormGroup;
  /** Valor recebido por e-mail; lido da URL uma vez e tirado dela em seguida. */
  private tokenRecuperacao = '';

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
    this.esqueciForm = this.fb.group({
      login: ['', [Validators.required, Validators.minLength(3)]],
    });
    this.redefinirForm = this.fb.group({
      novaSenha: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(50)]],
      confirmar: ['', [Validators.required]],
    });

    if (this.modo === 'redefinir') {
      this.tokenRecuperacao = this.route.snapshot.queryParamMap.get('token') ?? '';
      // O link é uma credencial: sai da barra de endereço e do histórico assim que lido.
      this.router.navigate([], { replaceUrl: true, queryParams: {} });
      if (!this.tokenRecuperacao) {
        this.mensagemErro = '❌ Este link está incompleto. Peça um novo.';
      }
    }
  }

  /** Pede o link. A mensagem é a mesma exista a conta ou não — de propósito. */
  public pedirLink(): void {
    if (this.esqueciForm.invalid) {
      this.esqueciForm.markAllAsTouched();
      this.mensagemErro = 'Informe o seu login para continuar.';
      return;
    }
    this.mensagemErro = '';
    this.mensagemSucesso = '';
    this.carregando = true;
    this.authService.esqueciSenha(this.esqueciForm.value.login.trim()).subscribe({
      next: () => {
        this.carregando = false;
        this.mensagemSucesso = '✅ Se a conta tiver um e-mail de recuperação cadastrado, enviamos um link para ele. '
          + 'Ele vale por 30 minutos. Sem e-mail cadastrado, peça a redefinição ao diretor.';
        this.esqueciForm.reset();
      },
      error: (erro: ApiErro) => {
        this.carregando = false;
        this.mensagemErro = `❌ ${erro.message}`;
      },
    });
  }

  public definirNovaSenha(): void {
    const { novaSenha, confirmar } = this.redefinirForm.value;
    if (!this.tokenRecuperacao) {
      this.mensagemErro = '❌ Este link está incompleto. Peça um novo.';
      return;
    }
    if (this.redefinirForm.invalid) {
      this.redefinirForm.markAllAsTouched();
      this.mensagemErro = 'A nova senha precisa ter entre 8 e 50 caracteres.';
      return;
    }
    if (novaSenha !== confirmar) {
      this.mensagemErro = 'A confirmação não corresponde à nova senha.';
      return;
    }
    this.mensagemErro = '';
    this.carregando = true;
    this.authService.redefinirSenha(this.tokenRecuperacao, novaSenha).subscribe({
      next: () => {
        this.carregando = false;
        this.mensagemSucesso = '✅ Senha redefinida! Entre com a senha nova...';
        this.redefinirForm.reset();
        setTimeout(() => this.router.navigate(['/login']), 1800);
      },
      error: (erro: ApiErro) => {
        this.carregando = false;
        // A política de senha manda a mensagem exata no campo; o resto cai na faixa.
        this.mensagemErro = `❌ ${erro.fields?.['novaSenha'] ?? erro.message}`;
      },
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
