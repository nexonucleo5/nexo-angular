import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { forkJoin } from 'rxjs';
import { MatriculasService } from '../api/matriculas.service';
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
  private readonly matriculasApi = inject(MatriculasService);
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

  // ── Efetivação direto da fila ──────────────────────────────────────────────
  // A transição PENDENTE → ATIVA não pede mais nada além da decisão — obrigar a
  // abrir a lista, achar o aluno e abrir o modal era caminho comprido para um
  // clique. O que pede contexto (documentação, cancelamento) continua no modal,
  // via "Abrir".

  readonly efetivandoId = signal<number | null>(null);
  readonly filaErro = signal<string | null>(null);

  efetivar(p: PendenciaDTO): void {
    this.efetivandoId.set(p.matriculaId);
    this.filaErro.set(null);
    this.matriculasApi.atualizarStatus(p.matriculaId, 'ATIVA').subscribe({
      next: () => {
        this.efetivandoId.set(null);
        // Recarrega o painel inteiro: a efetivação mexe nos contadores e pode
        // tirar (ou não) o item da fila — o servidor é quem sabe.
        this.carregar();
      },
      error: (erro) => {
        this.efetivandoId.set(null);
        this.filaErro.set(erro?.error?.message ?? 'Não foi possível efetivar a matrícula.');
      },
    });
  }

  formatarData(iso: string): string {
    if (!iso) return '';
    return new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).toLocaleDateString('pt-BR');
  }

  /** Fila se drena pela idade: "há N dias" diz a urgência sem exigir conta de cabeça. */
  diasNaFila(iso: string): string {
    if (!iso) return '';
    const inicio = new Date(iso + (iso.length === 10 ? 'T00:00:00' : '')).getTime();
    const dias = Math.max(0, Math.floor((Date.now() - inicio) / 86_400_000));
    if (dias === 0) return 'hoje';
    if (dias === 1) return 'há 1 dia';
    return `há ${dias} dias`;
  }

  iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[partes.length - 1]?.[0] ?? '')).toUpperCase();
  }
}
