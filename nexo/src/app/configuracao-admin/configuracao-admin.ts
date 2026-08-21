import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracaoAdminService } from './configuracao-admin.service';
import { AdminNotificacoesConfig, CatalogoConfig } from './configuracao-admin.model';
import { AparenciaConfig } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-configuracao-admin',
  templateUrl: './configuracao-admin.html',
  styleUrl: './configuracao-admin.scss',
})
export class ConfiguracaoAdmin {
  private readonly configService = inject(ConfiguracaoAdminService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly catalogo = computed(() => this.settings().catalogo);
  readonly aparencia = computed(() => this.settings().aparencia);

  onNotificacoesChange(key: keyof AdminNotificacoesConfig, checked: boolean): void {
    this.configService.updateSection('notificacoes', { [key]: checked } as Partial<AdminNotificacoesConfig>);
  }

  onCatalogoChange(key: keyof CatalogoConfig, checked: boolean): void {
    this.configService.updateSection('catalogo', { [key]: checked } as Partial<CatalogoConfig>);
  }

  onAvisoCatalogoChange(valor: string): void {
    const limite = Number(valor);
    if (!Number.isFinite(limite) || limite < 1) return; // não persiste limite sem sentido
    this.configService.updateSection('catalogo', { avisarMateriaComMenosDeNConteudos: limite });
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
