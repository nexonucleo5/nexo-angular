import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracoesService } from './configuracoes.service';
import { UserSettings } from './configuracoes.model';

@Component({
  selector: 'app-configuracoes',
  imports: [CommonModule],
  templateUrl: './configuracoes.html',
  styleUrl: './configuracoes.scss',
})
export class Configuracoes implements OnInit {
  private readonly configService = inject(ConfiguracoesService);
  private readonly router = inject(Router);


  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly gamificacao = computed(() => this.settings().gamificacao);
  readonly estudos = computed(() => this.settings().estudos);
  readonly aparencia = computed(() => this.settings().aparencia);
  readonly privacidade = computed(() => this.settings().privacidade);

  ngOnInit(): void {
  }

  onNotificacoesChange<K extends keyof UserSettings['notificacoes']>(
    key: K,
    value: boolean
  ): void {
    this.configService.updateSection('notificacoes', { [key]: value } as any);

    if (this.configService.settings().notificacoes.avisoTarefasNovas) {
      this.configService.notificar('Nova tarefa!', 'Uma nova atividade foi publicada.');
    }
  }

  onGamificacaoChange<K extends keyof UserSettings['gamificacao']>(
    key: K,
    value: boolean
  ): void {
    this.configService.updateSection('gamificacao', { [key]: value } as any);
  }

  onEstudosChange(key: keyof UserSettings['estudos'], event: Event): void {
    const isChecked = (event.target as HTMLInputElement).checked;
    this.configService.updateSection('estudos', { [key]: isChecked });
  }

  onAparenciaChange(key: keyof UserSettings['aparencia'], checked: boolean): void {
    // Invertemos o valor explicitamente para garantir a troca
    this.configService.updateSection('aparencia', { [key]: checked });
  }

  onPrivacidadeChange<K extends keyof UserSettings['privacidade']>(
    key: K,
    value: boolean
  ): void {
    this.configService.updateSection('privacidade', { [key]: value } as any);
  }

  // Ações da conta
  // ---------------------------------------------------------------------------

  editarPerfil(): void {
    this.router.navigate(['/perfil']);
  }

  alterarSenha(): void {
    this.router.navigate(['/perfil']);
  }

  encerrarSessao(): void {
    // TODO: chamar AuthService.logout() e redirecionar
    this.router.navigate(['/login']);
  }
}


