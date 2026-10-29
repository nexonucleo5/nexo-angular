import { Component, DOCUMENT, DestroyRef, computed, inject, input, signal } from '@angular/core';

export type ModoLogo = 'auto' | 'claro' | 'escuro';

/**
 * Logo NEXO que acompanha o tema.
 *
 * A arte é dividida em duas peças que, uma embaixo da outra, formam a palavra:
 * a metade de cima das letras é a peça clara e a de baixo é a peça escura.
 * Cada tema acende a peça de cor oposta ao fundo — no modo escuro a clara, no
 * modo claro a escura — e a outra fica apagada, só segurando a palavra. Na
 * virada de tema a peça acesa recolhe para a linha do meio e a outra nasce dela.
 *
 * Peça de encaixe: basta `<app-logo-nexo />` em qualquer lugar. O tema é lido
 * direto do `data-theme` do <html> (aplicarTema, settings-store.ts), então não
 * depende de qual store de configurações está ativo.
 *
 * @example <app-logo-nexo largura="180px" />
 * @example <app-logo-nexo modo="escuro" />   <!-- fixa como no modo escuro -->
 * @example <app-logo-nexo [fantasma]="0.15" /> <!-- peça apagada mais sumida -->
 */
@Component({
  selector: 'app-logo-nexo',
  standalone: true,
  templateUrl: './logo-nexo.html',
  styleUrl: './logo-nexo.scss',
  host: {
    role: 'img',
    'aria-label': 'NEXO',
    '[style.width]': 'largura()',
    '[style.--logo-fantasma]': 'fantasma()',
    '[class.escuro]': 'escuro()',
    '[class.animar]': 'animar()',
  },
})
export class LogoNexo {
  /** Largura da logo (qualquer unidade CSS); a altura segue a proporção da arte. */
  readonly largura = input('160px');
  /** 'auto' segue o tema do sistema; 'claro'/'escuro' fixam a logo como naquele tema. */
  readonly modo = input<ModoLogo>('auto');
  /** Opacidade da peça apagada (0 some com ela; 1 mostra as duas iguais). */
  readonly fantasma = input(0.35);

  private readonly temaEscuro = signal(false);
  /**
   * Só anima depois da primeira troca de tema: ao abrir a página a logo já
   * aparece pronta, sem a metade "nascendo".
   */
  readonly animar = signal(false);

  readonly escuro = computed(() => {
    const modo = this.modo();
    return modo === 'auto' ? this.temaEscuro() : modo === 'escuro';
  });

  constructor() {
    const raiz = inject(DOCUMENT).documentElement;
    const ler = () => raiz.getAttribute('data-theme') === 'dark';
    this.temaEscuro.set(ler());

    const observador = new MutationObserver(() => {
      const escuro = ler();
      if (escuro === this.temaEscuro()) return;
      this.animar.set(true);
      this.temaEscuro.set(escuro);
    });
    observador.observe(raiz, { attributes: true, attributeFilter: ['data-theme'] });
    inject(DestroyRef).onDestroy(() => observador.disconnect());
  }
}
