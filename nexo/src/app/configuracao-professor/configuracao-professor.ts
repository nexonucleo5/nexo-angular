import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ConfiguracaoProfessorService } from './configuracao-professor.service';
import {
  ProfessorNotificacoesConfig,
  AvaliacaoConfig,
  ProfessorPrivacidadeConfig,
} from './configuracao-professor.model';
import { AparenciaConfig } from '../configuracoes/settings-store';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-configuracao-professor',
  templateUrl: './configuracao-professor.html',
  styleUrl: './configuracao-professor.scss',
})
export class ConfiguracaoProfessor {
  private readonly configService = inject(ConfiguracaoProfessorService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly settings = this.configService.settings;

  readonly notificacoes = computed(() => this.settings().notificacoes);
  readonly avaliacao = computed(() => this.settings().avaliacao);
  readonly disponibilidade = computed(() => this.settings().disponibilidade);
  readonly aparencia = computed(() => this.settings().aparencia);
  readonly privacidade = computed(() => this.settings().privacidade);

  /** Mensagem de erro da validação início < fim (feedback imediato; backend reforça). */
  readonly erroHorario = signal<string | null>(null);

  onNotificacoesChange(key: keyof ProfessorNotificacoesConfig, checked: boolean): void {
    this.configService.updateSection('notificacoes', { [key]: checked } as Partial<ProfessorNotificacoesConfig>);
  }

  onAvaliacaoChange(key: keyof AvaliacaoConfig, checked: boolean): void {
    this.configService.updateSection('avaliacao', { [key]: checked } as Partial<AvaliacaoConfig>);
  }

  onContatoForaHorarioChange(checked: boolean): void {
    this.configService.updateSection('disponibilidade', { aceitarContatoForaHorario: checked });
  }

  onHorarioChange(campo: 'horarioInicio' | 'horarioFim', valor: string): void {
    const atual = this.disponibilidade();
    const inicio = campo === 'horarioInicio' ? valor : atual.horarioInicio;
    const fim = campo === 'horarioFim' ? valor : atual.horarioFim;

    if (!valor || inicio >= fim) {
      this.erroHorario.set('O horário de início deve ser anterior ao de fim.');
      return; // valor inválido não é persistido
    }

    this.erroHorario.set(null);
    this.configService.updateSection('disponibilidade', { [campo]: valor });
  }

  onAparenciaChange(key: keyof AparenciaConfig, checked: boolean): void {
    this.configService.updateSection('aparencia', { [key]: checked } as Partial<AparenciaConfig>);
  }

  onPrivacidadeChange(key: keyof ProfessorPrivacidadeConfig, checked: boolean): void {
    this.configService.updateSection('privacidade', { [key]: checked } as Partial<ProfessorPrivacidadeConfig>);
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
