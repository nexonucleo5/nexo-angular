import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracaoSecretariaService } from './configuracao-secretaria.service';
import { SecretariaNotificacoesConfig, DocumentosConfig } from './configuracao-secretaria.model';
import { AparenciaConfig } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-configuracao-secretaria',
  templateUrl: './configuracao-secretaria.html',
  styleUrl: './configuracao-secretaria.scss',
})
export class ConfiguracaoSecretaria {
  private readonly configService = inject(ConfiguracaoSecretariaService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly documentos = computed(() => this.settings().documentos);
  readonly aparencia = computed(() => this.settings().aparencia);

  readonly validadesDeclaracao = [15, 30, 60, 90];

  onNotificacoesChange(key: keyof SecretariaNotificacoesConfig, checked: boolean): void {
    this.configService.updateSection('notificacoes', { [key]: checked } as Partial<SecretariaNotificacoesConfig>);
  }

  onValidadeDeclaracaoChange(valor: string): void {
    this.configService.updateSection('documentos', { validadeDeclaracaoDias: Number(valor) });
  }

  onAvisoFilaChange(valor: string): void {
    const limite = Number(valor);
    if (!Number.isFinite(limite) || limite < 1) return; // não persiste limite sem sentido
    this.configService.updateSection('documentos', { avisarFilaAcimaDe: limite });
  }

  onAparenciaChange(key: keyof AparenciaConfig, checked: boolean): void {
    this.configService.updateSection('aparencia', { [key]: checked } as Partial<AparenciaConfig>);
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
