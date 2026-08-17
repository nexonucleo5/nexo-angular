import { CommonModule } from '@angular/common';
import { Component, Injector, Signal, computed, effect, inject, signal, untracked } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Menu } from './menu/menu';
import { MenuUsuario } from './menu-usuario/menu-usuario';
import { MenuDiretor } from './menu-diretor/menu-diretor';
import { MenuProfessor } from './menu-professor/menu-professor';
import { MenuSecretaria } from './menu-secretaria/menu-secretaria';
import { AuthService, RoleCliente } from './services/auth.service';
import { aplicarTema, temaSalvoEscuro } from './configuracoes/settings-store';
import { ConfiguracaoAlunoService } from './configuracao-aluno/configuracao-aluno.service';
import { ConfiguracaoProfessorService } from './configuracao-professor/configuracao-professor.service';
import { ConfiguracaoDiretorService } from './configuracao-diretor/configuracao-diretor.service';
import { ConfiguracaoSecretariaService } from './configuracao-secretaria/configuracao-secretaria.service';

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
    MenuSecretaria,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  host: {
    '(document:keydown.escape)': 'fecharSeCobreATela()',
  },
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
   * Sidebar aberta. O botão dos 3 riscos existe em qualquer largura, mas a barra
   * se comporta de dois jeitos — os mesmos dois do styles.scss:
   *
   * - de 768px para cima ela empurra o conteúdo, que reflui ao lado;
   * - abaixo disso ela cobre a tela e é a própria navegação, porque não sobraria
   *   largura útil para os dois (ver o @media da seção 3.2).
   *
   * Começa aberta só onde sobra largura para os dois. Depois disso quem manda é
   * o botão: não há listener de resize porque reagir a ele desfaria a escolha
   * que a pessoa acabou de fazer no clique.
   */
  readonly menuAberto = signal(App.cabeLadoALado());

  private static cabeLadoALado(): boolean {
    return window.matchMedia('(min-width: 992px)').matches;
  }

  /** Largura em que a barra cobre a tela inteira em vez de empurrar. */
  private static cobreATela(): boolean {
    return window.matchMedia('(max-width: 767.98px)').matches;
  }

  constructor() {
    aplicarTema(this.temaLocal());

    // Instancia o service de configurações do perfil logado: sincroniza com a
    // API (GET /api/configuracoes) e aplica tema/animações sem precisar
    // visitar a tela de configurações.
    effect(() => {
      const role = this.authService.usuarioLogado()?.role ?? null;
      this.settingsAtivo.set(role ? untracked(() => this.servicoPara(role)) : null);

      // O modo foco é uma configuração do aluno, mas a classe mora no <body> e
      // sobrevive à troca de sessão. Sem limpar aqui, um aluno com o modo ligado
      // deixava o próximo perfil sem barra lateral — e sem a tela de estudos do
      // aluno para desligar, ninguém conseguia mais trazê-la de volta.
      if (role !== 'aluno') {
        document.body.classList.remove('modo-foco-ativo');
      }
    });

    // Entrou ou saiu, a barra volta ao padrão da largura atual.
    effect(() => {
      this.authService.usuarioLogado();
      untracked(() => this.menuAberto.set(App.cabeLadoALado()));
    });

    // No celular a barra cobre a tela, então tocar num link precisa fechá-la —
    // senão a tela recém-aberta fica atrás dela. Onde ela empurra, fica aberta
    // de propósito: não cobre nada e serve para ir direto à próxima tela.
    this.router.events
      .pipe(filter((evento) => evento instanceof NavigationEnd))
      .subscribe(() => {
        if (App.cobreATela()) this.menuAberto.set(false);
      });

    // Trava o scroll do fundo enquanto a barra cobre a tela: sem isso o dedo que
    // quer rolar a lista de links rola a página atrás dela. A classe só tem
    // efeito dentro do @media do celular (seção 3.2), então uma janela
    // redimensionada para o desktop nunca fica com o body preso.
    effect(() => {
      document.body.classList.toggle('menu-travado', this.menuAberto());
    });
  }

  alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  fecharMenu(): void {
    this.menuAberto.set(false);
  }

  /**
   * Esc só fecha onde a barra cobre a tela — ali ela prende quem está no
   * teclado. Onde ela empurra é um painel de navegação; fechá-la no Esc
   * derrubaria a navegação de quem só queria sair de um campo de busca.
   */
  fecharSeCobreATela(): void {
    if (App.cobreATela()) this.fecharMenu();
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
      case 'secretaria':
        return this.injector.get(ConfiguracaoSecretariaService);
      default:
        return this.injector.get(ConfiguracaoAlunoService);
    }
  }
}
