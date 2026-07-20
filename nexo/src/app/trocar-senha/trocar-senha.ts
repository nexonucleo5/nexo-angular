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
  readonly mensagemErro = signal('');
  readonly mensagemSucesso = signal('');

  readonly form = this.fb.group({
    senhaAtual: ['', [Validators.required]],
    novaSenha: ['', [Validators.required, Validators.minLength(8)]],
    confirmar: ['', [Validators.required]],
  });

  salvar(): void {
    this.mensagemErro.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.mensagemErro.set('Preencha todos os campos (nova senha com no mínimo 8 caracteres).');
      return;
    }
    const { senhaAtual, novaSenha, confirmar } = this.form.value;
    if (novaSenha !== confirmar) {
      this.mensagemErro.set('A confirmação não corresponde à nova senha.');
      return;
    }

    this.enviando.set(true);
    this.usuarios.trocarSenha({ senhaAtual: senhaAtual!, novaSenha: novaSenha! }).subscribe({
      next: () => {
        this.enviando.set(false);
        this.mensagemSucesso.set('✅ Senha alterada com sucesso!');
        this.form.reset();
        setTimeout(() => this.router.navigate(['/configuracoes']), 1500);
      },
      error: (erro: ApiErro) => {
        this.enviando.set(false);
        this.mensagemErro.set(erro?.status === 400 ? 'Senha atual incorreta.' : (erro?.message || 'Falha ao alterar a senha.'));
      },
    });
  }

  voltar(): void {
    this.router.navigate(['/configuracoes']);
  }
}
