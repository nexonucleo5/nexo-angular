import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RecuperacaoSenhaService } from '../api/recuperacao-senha.service';
import { ApiErro } from '../core/api.models';

/**
 * Primeiro passo da recuperação: a pessoa informa o login e recebe o link por e-mail.
 */
@Component({
  selector: 'app-recuperar-senha',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './recuperar-senha.html',
  styleUrl: './recuperar-senha.scss',
})
export class RecuperarSenha {
  private readonly fb = inject(FormBuilder);
  private readonly recuperacao = inject(RecuperacaoSenhaService);

  readonly enviando = signal(false);
  readonly enviado = signal(false);
  readonly mensagemErro = signal('');

  readonly form = this.fb.group({
    login: ['', [Validators.required]],
  });

  enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.mensagemErro.set('Informe o seu login para continuar.');
      return;
    }

    this.mensagemErro.set('');
    this.enviando.set(true);

    this.recuperacao.solicitar(this.form.value.login!).subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviado.set(true);
      },
      error: (erro: ApiErro) => {
        this.enviando.set(false);
        // O servidor responde igual exista ou não a conta, justamente para não entregar
        // quem está cadastrado. A tela precisa acompanhar: o único erro que aparece aqui
        // é o excesso de pedidos (429), que não fala de conta nenhuma. Qualquer outra
        // falha vira a mesma confirmação — senão a diferença na tela reintroduziria, pelo
        // visual, a informação que o backend teve o cuidado de esconder.
        if (erro?.status === 429) {
          this.mensagemErro.set(erro.message || 'Muitos pedidos. Tente novamente mais tarde.');
          return;
        }
        this.enviado.set(true);
      },
    });
  }
}
