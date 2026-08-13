import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RecuperacaoSenhaService } from '../api/recuperacao-senha.service';
import { ApiErro } from '../core/api.models';

/**
 * Segundo passo: a pessoa chega aqui pelo link do e-mail, com o token na query string,
 * e escolhe a senha nova. É a mesma tela do convite de primeiro acesso — quem acabou de
 * ser cadastrado também define a senha por aqui.
 */
@Component({
  selector: 'app-redefinir-senha',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './redefinir-senha.html',
  styleUrl: './redefinir-senha.scss',
})
export class RedefinirSenha {
  private readonly fb = inject(FormBuilder);
  private readonly recuperacao = inject(RecuperacaoSenhaService);
  private readonly rota = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Vem do link do e-mail. Sem ele não há o que redefinir. */
  private readonly token = this.rota.snapshot.queryParamMap.get('token') ?? '';

  readonly semToken = signal(!this.token);
  readonly enviando = signal(false);
  readonly concluido = signal(false);
  readonly mensagemErro = signal('');

  readonly form = this.fb.group({
    novaSenha: ['', [Validators.required, Validators.minLength(8)]],
    confirmar: ['', [Validators.required]],
  });

  salvar(): void {
    this.mensagemErro.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.mensagemErro.set('A senha precisa ter no mínimo 8 caracteres.');
      return;
    }

    const { novaSenha, confirmar } = this.form.value;
    if (novaSenha !== confirmar) {
      this.mensagemErro.set('A confirmação não corresponde à nova senha.');
      return;
    }

    this.enviando.set(true);
    this.recuperacao.redefinir(this.token, novaSenha!).subscribe({
      next: () => {
        this.enviando.set(false);
        this.concluido.set(true);
        // As sessões antigas foram revogadas no servidor: quem estava logado em outro
        // dispositivo precisa entrar de novo, com a senha que acabou de ser escolhida.
        setTimeout(() => this.router.navigate(['/login']), 2500);
      },
      error: (erro: ApiErro) => {
        this.enviando.set(false);
        // 400 aqui é sempre link inválido, vencido ou já usado — o servidor não separa os
        // casos de propósito, e a tela oferece o caminho de saída em vez do diagnóstico.
        this.mensagemErro.set(
          erro?.status === 400
            ? 'Este link não vale mais: ele expirou ou já foi usado. Peça um novo na tela de acesso.'
            : erro?.message || 'Não foi possível redefinir a senha. Tente novamente.',
        );
      },
    });
  }

  irParaLogin(): void {
    this.router.navigate(['/login']);
  }

  pedirNovoLink(): void {
    this.router.navigate(['/recuperar-senha']);
  }
}
