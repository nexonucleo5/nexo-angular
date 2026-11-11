import { Component, ElementRef, input, signal, viewChild } from '@angular/core';

/**
 * Player de áudio do chat. O <audio controls> nativo é uma pílula branca no Chrome
 * que destoa do tema escuro e da bolha roxa; aqui o <audio> fica escondido e a
 * interface (play/pausa, barra e tempo) herda a cor da bolha via currentColor.
 */
@Component({
  selector: 'app-chat-audio',
  standalone: true,
  template: `
    <audio #audio [src]="src()" preload="metadata"
           (loadedmetadata)="aoCarregar()" (durationchange)="aoCarregar()"
           (timeupdate)="atual.set(audio.currentTime)"
           (play)="tocando.set(true)" (pause)="tocando.set(false)"
           (ended)="aoTerminar()"></audio>

    <button type="button" class="play" (click)="alternar()"
            [attr.aria-label]="tocando() ? 'Pausar áudio' : 'Tocar áudio'">
      @if (tocando()) {
        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="6.5" y="5" width="4" height="14" rx="1.2"/><rect x="13.5" y="5" width="4" height="14" rx="1.2"/></svg>
      } @else {
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z"/></svg>
      }
    </button>

    <div class="trilha">
      <input type="range" min="0" [max]="duracao() || 0" step="0.1" [value]="atual()"
             [style.--progresso.%]="duracao() ? (atual() / duracao()) * 100 : 0"
             (input)="buscar($event)" aria-label="Posição do áudio">
      <span class="tempo">{{ formatar(tocando() || atual() > 0 ? atual() : duracao()) }}</span>
    </div>
  `,
  styles: `
    :host {
      display: flex; align-items: center; gap: 10px;
      width: 260px; max-width: 100%;
      padding: 4px 2px;
    }
    audio { display: none; }

    .play {
      flex: 0 0 auto;
      width: 38px; height: 38px; border-radius: 50%;
      border: none; padding: 0;
      display: grid; place-items: center;
      background: currentColor;
      cursor: pointer;
      svg { width: 18px; height: 18px; fill: var(--bolha-fundo, #1b1530); }
      &:focus-visible { outline: 2px solid currentColor; outline-offset: 2px; }
    }

    .trilha { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }

    input[type='range'] {
      -webkit-appearance: none; appearance: none;
      width: 100%; height: 4px; margin: 6px 0 0;
      border-radius: 4px; cursor: pointer;
      background: linear-gradient(to right,
        currentColor 0 var(--progresso, 0%),
        color-mix(in srgb, currentColor 28%, transparent) var(--progresso, 0%) 100%);
      &::-webkit-slider-thumb {
        -webkit-appearance: none; appearance: none;
        width: 12px; height: 12px; border-radius: 50%;
        background: currentColor; border: none;
      }
      &::-moz-range-thumb {
        width: 12px; height: 12px; border-radius: 50%;
        background: currentColor; border: none;
      }
    }

    .tempo { font-size: 11px; opacity: 0.75; font-variant-numeric: tabular-nums; }
  `,
})
export class ChatAudio {
  readonly src = input.required<string>();

  readonly tocando = signal(false);
  readonly atual = signal(0);
  readonly duracao = signal(0);

  private readonly audio = viewChild.required<ElementRef<HTMLAudioElement>>('audio');

  aoCarregar(): void {
    const el = this.audio().nativeElement;
    // Áudio gravado em webm pelo Chrome chega com duração Infinity até ser percorrido:
    // pula para o fim para o navegador calcular, e volta.
    if (el.duration === Infinity) {
      el.currentTime = 1e7;
      el.addEventListener('timeupdate', () => { el.currentTime = 0; this.atual.set(0); }, { once: true });
      return;
    }
    if (Number.isFinite(el.duration)) this.duracao.set(el.duration);
  }

  alternar(): void {
    const el = this.audio().nativeElement;
    if (el.paused) void el.play();
    else el.pause();
  }

  buscar(ev: Event): void {
    const valor = Number((ev.target as HTMLInputElement).value);
    this.audio().nativeElement.currentTime = valor;
    this.atual.set(valor);
  }

  aoTerminar(): void {
    this.tocando.set(false);
    this.atual.set(0);
  }

  formatar(segundos: number): string {
    const s = Math.max(0, Math.round(segundos || 0));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }
}
