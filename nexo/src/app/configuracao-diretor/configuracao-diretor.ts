import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracaoDiretorService } from './configuracao-diretor.service';
import {
  DiretorNotificacoesConfig,
  GestaoConfig,
  IntegracoesConfig,
  DiretorPrivacidadeConfig,
} from './configuracao-diretor.model';
import { AparenciaConfig } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-configuracao-diretor',
  templateUrl: './configuracao-diretor.html',
  styleUrl: './configuracao-diretor.scss',
})
export class ConfiguracaoDiretor {
  private readonly configService = inject(ConfiguracaoDiretorService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly institucional = computed(() => this.settings().institucional);
  readonly gestao = computed(() => this.settings().gestao);
  readonly integracoes = computed(() => this.settings().integracoes);
  readonly aparencia = computed(() => this.settings().aparencia);
  readonly privacidade = computed(() => this.settings().privacidade);

  readonly anosLetivos = [2024, 2025, 2026, 2027];

  onNotificacoesChange(key: keyof DiretorNotificacoesConfig, checked: boolean): void {
    this.configService.updateSection('notificacoes', { [key]: checked } as Partial<DiretorNotificacoesConfig>);
  }

  onNomeInstituicaoChange(valor: string): void {
    const nome = valor.trim();
    if (!nome) return; // não persiste nome vazio
    this.configService.updateSection('institucional', { nomeInstituicao: nome });
  }

  onAnoLetivoChange(valor: string): void {
    this.configService.updateSection('institucional', { anoLetivoAtivo: Number(valor) });
  }

  onGestaoChange(key: keyof GestaoConfig, checked: boolean): void {
    this.configService.updateSection('gestao', { [key]: checked } as Partial<GestaoConfig>);
  }

  onIntegracoesChange(key: keyof IntegracoesConfig, checked: boolean): void {
    this.configService.updateSection('integracoes', { [key]: checked } as Partial<IntegracoesConfig>);
  }

  onAparenciaChange(key: keyof AparenciaConfig, checked: boolean): void {
    this.configService.updateSection('aparencia', { [key]: checked } as Partial<AparenciaConfig>);
  }

  onPrivacidadeChange(key: keyof DiretorPrivacidadeConfig, checked: boolean): void {
    this.configService.updateSection('privacidade', { [key]: checked } as Partial<DiretorPrivacidadeConfig>);
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
