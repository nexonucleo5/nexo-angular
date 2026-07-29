import { CommonModule } from '@angular/common';
import { Component, Injector, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Menu } from './menu/menu';
import { MenuUsuario } from './menu-usuario/menu-usuario';
import { MenuDiretor } from './menu-diretor/menu-diretor';
import { MenuProfessor } from './menu-professor/menu-professor';
import { AuthService, RoleCliente } from './services/auth.service';
import { aplicarTema, temaSalvoEscuro } from './configuracoes/settings-store';
import { ConfiguracaoAlunoService } from './configuracao-aluno/configuracao-aluno.service';
import { ConfiguracaoProfessorService } from './configuracao-professor/configuracao-professor.service';
import { ConfiguracaoDiretorService } from './configuracao-diretor/configuracao-diretor.service';

/** Contrato mínimo comum aos três services de configuração por perfil. */
interface PerfilSettings {
  isDarkMode: Signal<boolean>;
  alternarTema(): void;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    Menu,
    MenuUsuario,
    MenuDiretor,
    MenuProfessor,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  public authService = inject(AuthService);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);

  /** Tema antes do login (cache compartilhado gravado pelo SettingsStore). */
  private readonly temaLocal = signal(temaSalvoEscuro());
  private readonly settingsAtivo = signal<PerfilSettings | null>(null);

  readonly temaEscuro = computed(() => this.settingsAtivo()?.isDarkMode() ?? this.temaLocal());

  /**
   * Sidebar aberta no mobile. No desktop a barra é fixa e este estado não tem
   * efeito nenhum — quem decide é o @media do styles.scss.
   */
  readonly menuAberto = signal(false);

  constructor() {
    aplicarTema(this.temaLocal());

    // Instancia o service de configurações do perfil logado: sincroniza com a
    // API (GET /api/configuracoes) e aplica tema/animações sem precisar
    // visitar a tela de configurações.
    effect(() => {
      const role = this.authService.usuarioLogado()?.role ?? null;
      this.settingsAtivo.set(role ? untracked(() => this.servicoPara(role)) : null);
    });

    // Trocou de tela, fecha a barra: no mobile ela cobre o conteúdo inteiro e
    // ficaria por cima da página recém-aberta.
    this.router.events
      .pipe(filter((evento) => evento instanceof NavigationEnd))
      .subscribe(() => this.menuAberto.set(false));

    // Logado ou deslogado, nunca começa com a barra aberta.
    effect(() => {
      this.authService.usuarioLogado();
      untracked(() => this.menuAberto.set(false));
    });
  }

  alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  fecharMenu(): void {
    this.menuAberto.set(false);
  }

  alternarTema(): void {
    const ativo = this.settingsAtivo();
    if (ativo) {
      // Persiste na seção Aparência do perfil (localStorage + PATCH na API)
      ativo.alternarTema();
      return;
    }
    this.temaLocal.update((escuro) => !escuro);
    aplicarTema(this.temaLocal());
  }

  private servicoPara(role: RoleCliente): PerfilSettings {
    switch (role) {
      case 'professor':
        return this.injector.get(ConfiguracaoProfessorService);
      case 'diretor':
        return this.injector.get(ConfiguracaoDiretorService);
      default:
        return this.injector.get(ConfiguracaoAlunoService);
    }
  }
}
