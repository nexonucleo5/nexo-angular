import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminService } from '../api/admin.service';
import { ContaDTO, PapelConta } from '../core/api.models';

const PAPEL_LABEL: Record<PapelConta, string> = {
  ALUNO: 'Aluno',
  PROFESSOR: 'Professor',
  DIRETOR: 'Diretor',
  ADMIN: 'Administrador',
};

/**
 * Gestão de contas de acesso.
 *
 * <p>É deliberadamente só isso: quem entra, com que perfil, e se a conta está
 * ligada. Não há ficha de pessoa para abrir porque o sistema não guarda nenhuma —
 * nome e login estão aqui apenas para se saber de quem é a conta que se vai
 * desativar.
 */
@Component({
  selector: 'app-contas',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contas.html',
  styleUrl: './contas.scss',
})
export class Contas {
  private readonly admin = inject(AdminService);
  private readonly route = inject(ActivatedRoute);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly contas = signal<ContaDTO[]>([]);

  readonly buscaTermo = signal('');
  readonly papelSelecionado = signal<'Todos' | PapelConta>('Todos');
  readonly papelOpcoes: Array<'Todos' | PapelConta> = ['Todos', 'ALUNO', 'PROFESSOR', 'DIRETOR', 'ADMIN'];

  /** Vindo do painel (?inativas=true): abre já mostrando o que precisa de ação. */
  readonly apenasInativas = signal(false);

  readonly contasFiltradas = computed(() => {
    const termo = this.buscaTermo().toLowerCase();
    const papel = this.papelSelecionado();
    const soInativas = this.apenasInativas();
    return this.contas().filter((c) => {
      const buscaOk = !termo
        || c.nome.toLowerCase().includes(termo)
        || c.login.toLowerCase().includes(termo);
      return buscaOk
        && (papel === 'Todos' || c.papel === papel)
        && (!soInativas || !c.ativo);
    });
  });

  readonly totalAtivas = computed(() => this.contas().filter((c) => c.ativo).length);
  readonly totalInativas = computed(() => this.contas().filter((c) => !c.ativo).length);

  constructor() {
    this.apenasInativas.set(this.route.snapshot.queryParamMap.get('inativas') === 'true');
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    // size alto porque o filtro é local: a base de contas de uma escola cabe
    // numa página, e paginar no servidor quebraria a busca enquanto se digita.
    this.admin.contas({ size: 100 }).subscribe({
      next: (page) => {
        this.contas.set(page.content);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  papelLabel(p: PapelConta): string {
    return PAPEL_LABEL[p];
  }

  iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    return ((partes[0]?.[0] ?? '') + (partes[partes.length - 1]?.[0] ?? '')).toUpperCase();
  }

  formatarData(iso: string): string {
    return iso ? new Date(iso).toLocaleDateString('pt-BR') : '';
  }

  // ── Ações ─────────────────────────────────────────────────────────────────

  readonly acaoEmCurso = signal<number | null>(null);
  readonly acaoErro = signal<string | null>(null);
  readonly acaoSucesso = signal<string | null>(null);

  alternarAtivo(c: ContaDTO): void {
    this.acaoEmCurso.set(c.id);
    this.limparFeedback();
    this.admin.atualizarAtivo(c.id, !c.ativo).subscribe({
      next: (atualizada) => {
        this.contas.update((lista) => lista.map((x) => (x.id === atualizada.id ? atualizada : x)));
        this.acaoEmCurso.set(null);
        this.acaoSucesso.set(
          atualizada.ativo
            ? `Conta de ${atualizada.nome} reativada.`
            : `Conta de ${atualizada.nome} desativada — as sessões abertas foram encerradas.`,
        );
      },
      error: (erro) => {
        this.acaoEmCurso.set(null);
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível alterar a conta.');
      },
    });
  }

  /** A senha nova aparece uma única vez — daí o cartão com o botão de copiar. */
  readonly senhaGerada = signal<{ login: string; senha: string } | null>(null);

  redefinirSenha(c: ContaDTO): void {
    this.acaoEmCurso.set(c.id);
    this.limparFeedback();
    this.admin.redefinirSenha(c.id).subscribe({
      next: (resposta) => {
        this.acaoEmCurso.set(null);
        this.senhaGerada.set({ login: resposta.login, senha: resposta.senhaProvisoria });
      },
      error: (erro) => {
        this.acaoEmCurso.set(null);
        this.acaoErro.set(erro?.error?.message ?? 'Não foi possível redefinir a senha.');
      },
    });
  }

  fecharSenha(): void {
    this.senhaGerada.set(null);
    this.copiado.set(false);
  }

  readonly copiado = signal(false);
  private timerCopiado: ReturnType<typeof setTimeout> | null = null;

  copiarSenha(): void {
    const s = this.senhaGerada();
    if (!s) return;
    navigator.clipboard.writeText(`Login: ${s.login}\nSenha provisória: ${s.senha}`).then(() => {
      this.copiado.set(true);
      if (this.timerCopiado) clearTimeout(this.timerCopiado);
      this.timerCopiado = setTimeout(() => this.copiado.set(false), 2500);
    });
  }

  private limparFeedback(): void {
    this.acaoErro.set(null);
    this.acaoSucesso.set(null);
  }
}
