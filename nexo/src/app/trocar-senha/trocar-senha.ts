import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { UsuariosService } from '../api/usuarios.service';
import { ApiErro } from '../core/api.models';

@Component({
  selector: 'app-trocar-senha',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './trocar-senha.html',
  styleUrl: './trocar-senha.scss',
})
export class TrocarSenha {
  private readonly fb = inject(FormBuilder);
  private readonly usuarios = inject(UsuariosService);
  private readonly router = inject(Router);

  readonly enviando = signal(false);
  /** Erro que não pertence a um campo específico (rede, 500, sessão expirada). */
  readonly mensagemErro = signal('');
  readonly mensagemSucesso = signal('');

  /**
   * Um erro por campo, para a mensagem aparecer embaixo do controle que a causou.
   *
   * <p>Antes existia só a faixa no topo, e a tela decidia o texto pelo status HTTP:
   * qualquer 400 virava "Senha atual incorreta". Como a recusa de uma senha fraca também
   * é 400, tentar trocar para "12345678" acusava a senha atual — que estava certa — e o
   * usuário ficava redigitando o campo errado. Agora o texto e o campo vêm do backend,
   * pelo envelope de erro ({@code message} + {@code fields}).
   */
  readonly erroSenhaAtual = signal('');
  readonly erroNovaSenha = signal('');
  readonly erroConfirmar = signal('');

  readonly form = this.fb.group({
    senhaAtual: ['', [Validators.required]],
    novaSenha: ['', [Validators.required, Validators.minLength(8)]],
    confirmar: ['', [Validators.required]],
  });

  salvar(): void {
    this.limparErros();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Mesmo nas checagens locais o erro vai para o campo que o causou, para a tela se
      // comportar igual antes e depois da ida ao servidor.
      if (this.form.controls.senhaAtual.invalid) {
        this.erroSenhaAtual.set('Informe a senha atual.');
      }
      if (this.form.controls.novaSenha.hasError('required')) {
        this.erroNovaSenha.set('Informe a nova senha.');
      } else if (this.form.controls.novaSenha.hasError('minlength')) {
        this.erroNovaSenha.set('A nova senha precisa ter pelo menos 8 caracteres.');
      }
      if (this.form.controls.confirmar.invalid) {
        this.erroConfirmar.set('Repita a nova senha.');
      }
      return;
    }

    const { senhaAtual, novaSenha, confirmar } = this.form.value;
    if (novaSenha !== confirmar) {
      this.erroConfirmar.set('A confirmação não corresponde à nova senha.');
      return;
    }

    this.enviando.set(true);
    this.usuarios.trocarSenha({ senhaAtual: senhaAtual!, novaSenha: novaSenha! }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.mensagemSucesso.set('✅ Senha alterada com sucesso! As outras sessões foram encerradas.');
        this.form.reset();
        setTimeout(() => this.router.navigate(['/configuracoes']), 1500);
      },
      error: (erro: ApiErro) => {
        this.enviando.set(false);
        this.aplicarErro(erro);
      },
    });
  }

  /**
   * Distribui a resposta de erro pelos campos. O backend manda {@code fields} com a chave
   * exata do controle ({@code senhaAtual} ou {@code novaSenha}) — cada regra recusada tem
   * a sua mensagem. Sobrando algo sem campo, cai na faixa do topo.
   */
  private aplicarErro(erro: ApiErro): void {
    const campos = erro?.fields ?? {};
    this.erroSenhaAtual.set(campos['senhaAtual'] ?? '');
    this.erroNovaSenha.set(campos['novaSenha'] ?? '');

    const jaMostrado = !!(this.erroSenhaAtual() || this.erroNovaSenha());
    if (!jaMostrado) {
      this.mensagemErro.set(erro?.message || 'Não foi possível alterar a senha. Tente novamente.');
    }
  }

  private limparErros(): void {
    this.mensagemErro.set('');
    this.mensagemSucesso.set('');
    this.erroSenhaAtual.set('');
    this.erroNovaSenha.set('');
    this.erroConfirmar.set('');
  }

  voltar(): void {
    this.router.navigate(['/configuracoes']);
  }
}
