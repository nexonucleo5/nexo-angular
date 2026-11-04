import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../api/admin.service';
import { HoraDTO, LogAplicacaoDTO, NivelLog, ProblemaDTO, ResumoSistemaDTO } from '../core/api.models';

const NIVEL_LABEL: Record<NivelLog, string> = { INFO: 'INFO', WARN: 'AVISO', ERROR: 'ERRO' };
const NIVEL_CLASSE: Record<NivelLog, string> = {
  INFO: 'chip-info',
  WARN: 'chip-aviso',
  ERROR: 'chip-erro',
};

/**
 * Saúde do sistema: o log da aplicação (ERROR/WARN) e um resumo do que olhar primeiro.
 *
 * <p>É o complemento da aba de atividade. Aquela conta o que as pessoas fizeram; esta,
 * o que o sistema viveu — exceção não tratada, acesso negado, requisição lenta. Cada
 * linha traz o id da requisição, o mesmo do cabeçalho X-Request-Id da resposta, para
 * ligar um relato ("deu erro às 14h") ao registro certo.
 */
@Component({
  selector: 'app-logs-sistema',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './logs-sistema.html',
  styleUrls: ['../auditoria/logs-tabela.scss', './logs-sistema.scss'],
})
export class LogsSistema {
  private readonly admin = inject(AdminService);

  readonly tamanhoPagina = 25;

  readonly resumo = signal<ResumoSistemaDTO | null>(null);
  readonly resumoErro = signal(false);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly logs = signal<LogAplicacaoDTO[]>([]);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(0);
  readonly total = signal(0);

  readonly busca = signal('');
  readonly nivel = signal<'' | NivelLog>('');
  readonly periodo = signal(1);

  /** Ids com o stack trace aberto. */
  readonly abertos = signal<ReadonlySet<number>>(new Set());

  readonly nivelOpcoes: NivelLog[] = ['ERROR', 'WARN', 'INFO'];
  readonly periodoOpcoes: ReadonlyArray<{ dias: number; label: string }> = [
    { dias: 1, label: 'Últimas 24 horas' },
    { dias: 7, label: 'Últimos 7 dias' },
    { dias: 30, label: 'Últimos 30 dias' },
    { dias: 0, label: 'Todo o período' },
  ];

  readonly memoriaPercentual = computed(() => {
    const r = this.resumo();
    return r && r.heapMaxMb > 0 ? Math.round((r.heapUsadoMb / r.heapMaxMb) * 100) : 0;
  });

  readonly totalErros = computed(() => (this.resumo()?.porHora ?? []).reduce((n, h) => n + h.erros, 0));
  readonly totalAvisos = computed(() => (this.resumo()?.porHora ?? []).reduce((n, h) => n + h.avisos, 0));

  /** A maior hora define a escala; no mínimo 1, para um gráfico vazio não dividir por zero. */
  private readonly picoPorHora = computed(() =>
    Math.max(1, ...(this.resumo()?.porHora ?? []).map((h) => h.erros + h.avisos)));

  readonly estadoMemoria = computed(() => {
    const p = this.memoriaPercentual();
    return p >= 85 ? 'st-erro' : p >= 70 ? 'st-aviso' : 'st-ok';
  });

  readonly descricaoGrafico = computed(() =>
    `Últimas 24 horas: ${this.totalErros()} erros e ${this.totalAvisos()} avisos.`);

  /** "1–25", "26–50"… — a faixa que a tabela mostra agora. */
  readonly intervalo = computed(() => {
    const n = this.logs().length;
    if (n === 0) return '0';
    const inicio = this.pagina() * this.tamanhoPagina + 1;
    return `${inicio}–${inicio + n - 1}`;
  });

  private timerBusca: ReturnType<typeof setTimeout> | null = null;
  /** Descarta a resposta de uma consulta já superada por outra mais nova. */
  private requisicao = 0;

  constructor() {
    this.atualizar();
  }

  atualizar(): void {
    this.carregarResumo();
    this.carregar();
  }

  private carregarResumo(): void {
    this.resumoErro.set(false);
    this.admin.resumoSistema().subscribe({
      next: (r) => this.resumo.set(r),
      error: () => this.resumoErro.set(true),
    });
  }

  carregar(): void {
    const atual = ++this.requisicao;
    this.carregando.set(true);
    this.erro.set(false);
    this.admin
      .logsSistema({
        nivel: this.nivel() || undefined,
        busca: this.busca().trim() || undefined,
        periodo: this.periodo() || undefined,
        page: this.pagina(),
        size: this.tamanhoPagina,
      })
      .subscribe({
        next: (resposta) => {
          if (atual !== this.requisicao) return;
          this.logs.set(resposta.content);
          this.totalPaginas.set(resposta.totalPages);
          this.total.set(resposta.totalElements);
          this.abertos.set(new Set());
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

  /** Do resumo para a lista: clicar num problema mostra todas as ocorrências dele. */
  filtrarPor(p: ProblemaDTO): void {
    this.busca.set(p.caminho ?? p.mensagem);
    this.nivel.set(p.nivel);
    this.periodo.set(1);
    this.aplicarFiltro();
  }

  /** Altura da barra em % da coluna. Uma ocorrência nunca some: 6% é o piso visível. */
  pct(quantidade: number): number {
    return Math.max(6, (quantidade / this.picoPorHora()) * 100);
  }

  tituloHora(h: HoraDTO): string {
    const hora = new Date(h.hora).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    if (h.erros + h.avisos === 0) return `${hora} — sem ocorrências`;
    const partes = [];
    if (h.erros) partes.push(`${h.erros} ${h.erros === 1 ? 'erro' : 'erros'}`);
    if (h.avisos) partes.push(`${h.avisos} ${h.avisos === 1 ? 'aviso' : 'avisos'}`);
    return `${hora} — ${partes.join(', ')}`;
  }

  /** Falhas de login: poucas são rotina (senha errada), muitas indicam tentativa de invasão. */
  estadoFalhas(n: number): string {
    return n > 20 ? 'st-erro' : n > 0 ? 'st-aviso' : 'st-ok';
  }

  readonly copiadoId = signal<number | null>(null);
  private timerCopiado: ReturnType<typeof setTimeout> | null = null;

  copiar(l: LogAplicacaoDTO): void {
    if (!l.excecao) return;
    navigator.clipboard.writeText(l.excecao).then(() => {
      this.copiadoId.set(l.id);
      if (this.timerCopiado) clearTimeout(this.timerCopiado);
      this.timerCopiado = setTimeout(() => this.copiadoId.set(null), 2000);
    });
  }

  alternar(id: number): void {
    this.abertos.update((atual) => {
      const novo = new Set(atual);
      if (!novo.delete(id)) novo.add(id);
      return novo;
    });
  }

  nivelLabel(n: NivelLog): string {
    return NIVEL_LABEL[n] ?? n;
  }

  nivelClasse(n: NivelLog): string {
    return NIVEL_CLASSE[n] ?? 'status-ok';
  }

  /**
   * O filtro de requisição emite todo 403 e toda lentidão: citá-lo em cada linha seria ruído.
   * A classe só informa quando é ela que identifica o problema — um erro de verdade.
   */
  mostrarOrigem(origem: string): boolean {
    return !origem.endsWith('.RequisicaoLogFilter');
  }

  /** "com.nexo.service.AuthService" → "AuthService": o pacote é ruído numa lista. */
  origemCurta(origem: string): string {
    return origem.split('.').pop() ?? origem;
  }

  formatarUptime(segundos: number): string {
    const d = Math.floor(segundos / 86400);
    const h = Math.floor((segundos % 86400) / 3600);
    const m = Math.floor((segundos % 3600) / 60);
    if (d > 0) return `${d}d ${h}h`;
    if (h > 0) return `${h}h ${m}min`;
    return `${m}min`;
  }
}
