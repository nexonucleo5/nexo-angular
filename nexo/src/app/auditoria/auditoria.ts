import { Component, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../api/admin.service';
import { EventoAuditoriaDTO, TipoEvento } from '../core/api.models';
import { LogsSistema } from '../logs-sistema/logs-sistema';

const TIPO_LABEL: Record<TipoEvento, string> = {
  LOGIN: 'Login',
  LOGOUT: 'Logout',
  ACESSO: 'Acesso',
  ALTERACAO: 'Alteração',
  ERRO: 'Erro',
};

/** Erro em vermelho, alteração em âmbar (merece olho), o resto neutro. */
const TIPO_CLASSE: Record<TipoEvento, string> = {
  LOGIN: 'chip-info',
  LOGOUT: 'chip-info',
  ACESSO: 'chip-info',
  ALTERACAO: 'chip-aviso',
  ERRO: 'chip-erro',
};

/**
 * Logs — a trilha de auditoria (atividade dos usuários) e, na outra aba, o log da
 * aplicação. Só leitura e só para o administrador.
 *
 * <p>Ao contrário de Contas, o filtro roda no servidor: a trilha cresce com o uso
 * diário (login e logout geram um evento cada), então trazê-la inteira para filtrar
 * aqui não escala.
 */
@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CommonModule, FormsModule, LogsSistema],
  templateUrl: './auditoria.html',
  styleUrls: ['./logs-tabela.scss', './auditoria.scss'],
})
export class Auditoria {
  private readonly admin = inject(AdminService);

  /** Abre em "Sistema": o que está quebrado pesa mais do que o histórico de quem entrou. */
  readonly aba = signal<'atividade' | 'sistema'>('sistema');

  readonly tamanhoPagina = 25;

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly eventos = signal<EventoAuditoriaDTO[]>([]);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(0);
  readonly totalEventos = signal(0);

  readonly busca = signal('');
  readonly tipo = signal<'' | TipoEvento>('');
  readonly periodo = signal(7);

  readonly tipoOpcoes: TipoEvento[] = ['LOGIN', 'LOGOUT', 'ACESSO', 'ALTERACAO', 'ERRO'];
  readonly periodoOpcoes: ReadonlyArray<{ dias: number; label: string }> = [
    { dias: 1, label: 'Últimas 24 horas' },
    { dias: 7, label: 'Últimos 7 dias' },
    { dias: 30, label: 'Últimos 30 dias' },
    { dias: 90, label: 'Últimos 90 dias' },
    { dias: 0, label: 'Todo o período' },
  ];

  private timerBusca: ReturnType<typeof setTimeout> | null = null;
  /** Descarta a resposta de uma consulta já superada por outra mais nova. */
  private requisicao = 0;

  /** "1–25", "26–50"… — a faixa que a tabela mostra agora. */
  readonly intervalo = computed(() => {
    const n = this.eventos().length;
    if (n === 0) return '0';
    const inicio = this.pagina() * this.tamanhoPagina + 1;
    return `${inicio}–${inicio + n - 1}`;
  });

  constructor() {
    // A aba padrão é "Sistema"; a atividade só carrega quando alguém a abre.
    effect(() => {
      if (this.aba() === 'atividade' && !this.jaCarregou) {
        this.jaCarregou = true;
        untracked(() => this.carregar());
      }
    });
  }

  private jaCarregou = false;

  private readonly sistema = viewChild(LogsSistema);

  /** O botão do cabeçalho vale para a aba aberta. */
  atualizar(): void {
    if (this.aba() === 'sistema') this.sistema()?.atualizar();
    else this.carregar();
  }

  carregar(): void {
    const atual = ++this.requisicao;
    this.carregando.set(true);
    this.erro.set(false);
    this.admin
      .eventos({
        tipo: this.tipo() || undefined,
        busca: this.busca().trim() || undefined,
        periodo: this.periodo() || undefined,
        page: this.pagina(),
        size: this.tamanhoPagina,
      })
      .subscribe({
        next: (resposta) => {
          if (atual !== this.requisicao) return;
          this.eventos.set(resposta.content);
          this.totalPaginas.set(resposta.totalPages);
          this.totalEventos.set(resposta.totalElements);
          this.carregando.set(false);
        },
        error: () => {
          if (atual !== this.requisicao) return;
          this.erro.set(true);
          this.carregando.set(false);
        },
      });
  }

  /** Mudar qualquer filtro volta à primeira página — a atual pode nem existir mais. */
  aplicarFiltro(): void {
    this.pagina.set(0);
    this.carregar();
  }

  aoDigitar(termo: string): void {
    this.busca.set(termo);
    if (this.timerBusca) clearTimeout(this.timerBusca);
    this.timerBusca = setTimeout(() => this.aplicarFiltro(), 350);
  }

  irParaPagina(p: number): void {
    if (p < 0 || p >= this.totalPaginas() || p === this.pagina()) return;
    this.pagina.set(p);
    this.carregar();
  }

  /** O loopback IPv6 de quem roda local ("0:0:0:0:0:0:0:1") não diz nada a quem lê. */
  ipLegivel(ip: string | null): string {
    if (!ip) return '—';
    return ip === '0:0:0:0:0:0:0:1' || ip === '::1' ? 'localhost' : ip;
  }

  tipoLabel(t: TipoEvento): string {
    return TIPO_LABEL[t] ?? t;
  }

  tipoClasse(t: TipoEvento): string {
    return TIPO_CLASSE[t] ?? 'status-ok';
  }

}
