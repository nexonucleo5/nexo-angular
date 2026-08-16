import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { SecretariaService } from '../api/secretaria.service';
import { ConfiguracaoSecretariaService } from '../configuracao-secretaria/configuracao-secretaria.service';
import {
  DashboardSecretariaDTO,
  OcupacaoTurmaDTO,
  PendenciaDTO,
} from '../core/api.models';

/**
 * Painel operacional da secretaria: não é o dashboard executivo do diretor —
 * aqui o centro é a fila de trabalho (o que precisa de ação hoje) e as vagas
 * por turma (o que se consulta antes de matricular ou transferir).
 */
@Component({
  selector: 'app-secretaria-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './secretaria-dashboard.html',
  styleUrl: './secretaria-dashboard.scss',
})
export class SecretariaDashboard {
  private readonly secretaria = inject(SecretariaService);
  private readonly config = inject(ConfiguracaoSecretariaService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly dados = signal<DashboardSecretariaDTO | null>(null);
  readonly pendencias = signal<PendenciaDTO[]>([]);
  readonly ocupacao = signal<OcupacaoTurmaDTO[]>([]);

  /** A fila vira alerta quando passa do limite configurado em Documentos. */
  readonly filaEmAlerta = computed(() => {
    const d = this.dados();
    if (!d) return false;
    const fila = d.matriculasPendentes + d.documentacaoPendente;
    return fila > this.config.settings().documentos.avisarFilaAcimaDe;
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    forkJoin({
      dashboard: this.secretaria.dashboard(),
      pendencias: this.secretaria.pendencias(8),
      ocupacao: this.secretaria.ocupacao(),
    }).subscribe({
      next: ({ dashboard, pendencias, ocupacao }) => {
        this.dados.set(dashboard);
        this.pendencias.set(pendencias);
        this.ocupacao.set(ocupacao);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR');
  }

  iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[partes.length - 1]?.[0] ?? '')).toUpperCase();
  }
}
