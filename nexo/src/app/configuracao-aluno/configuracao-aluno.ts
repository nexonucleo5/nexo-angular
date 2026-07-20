import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracaoAlunoService } from './configuracao-aluno.service';
import {
  AlunoNotificacoesConfig,
  GamificacaoConfig,
  EstudosConfig,
  AcessibilidadeConfig,
  AlunoPrivacidadeConfig,
} from './configuracao-aluno.model';
import { AparenciaConfig } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-configuracao-aluno',
  templateUrl: './configuracao-aluno.html',
  styleUrl: './configuracao-aluno.scss',
})
export class ConfiguracaoAluno {
  private readonly configService = inject(ConfiguracaoAlunoService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly gamificacao = computed(() => this.settings().gamificacao);
  readonly estudos = computed(() => this.settings().estudos);
  readonly aparencia = computed(() => this.settings().aparencia);
  readonly acessibilidade = computed(() => this.settings().acessibilidade);
  readonly privacidade = computed(() => this.settings().privacidade);

  // Handlers padronizados na assinatura (key, checked) — correção do bug
  // do onEstudosChange apontado na Task 3.

  onNotificacoesChange(key: keyof AlunoNotificacoesConfig, checked: boolean): void {
    this.configService.updateSection('notificacoes', { [key]: checked } as Partial<AlunoNotificacoesConfig>);
    if (key === 'avisoTarefasNovas' && checked) {
      this.configService.notificar('Nova tarefa!', 'Uma nova atividade foi publicada.');
    }
  }

  onGamificacaoChange(key: keyof GamificacaoConfig, checked: boolean): void {
    this.configService.updateSection('gamificacao', { [key]: checked } as Partial<GamificacaoConfig>);
  }

  onEstudosChange(key: keyof EstudosConfig, checked: boolean): void {
    this.configService.updateSection('estudos', { [key]: checked } as Partial<EstudosConfig>);
  }

  onAparenciaChange(key: keyof AparenciaConfig, checked: boolean): void {
    this.configService.updateSection('aparencia', { [key]: checked } as Partial<AparenciaConfig>);
  }

  onAcessibilidadeChange(key: keyof AcessibilidadeConfig, checked: boolean): void {
    this.configService.updateSection('acessibilidade', { [key]: checked } as Partial<AcessibilidadeConfig>);
  }

  onPrivacidadeChange(key: keyof AlunoPrivacidadeConfig, checked: boolean): void {
    this.configService.updateSection('privacidade', { [key]: checked } as Partial<AlunoPrivacidadeConfig>);
  }

  // Ações da conta
  // ---------------------------------------------------------------------------

  editarPerfil(): void {
    this.router.navigate(['/perfil']);
  }

  alterarSenha(): void {
    this.router.navigate(['/trocar-senha']);
  }

  encerrarSessao(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
