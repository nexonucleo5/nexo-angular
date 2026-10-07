import { Component, DestroyRef, computed, effect, inject, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { LeituraVozService } from '../core/leitura-voz.service';

let proximoId = 0;

/**
 * Botão "Ouvir" ao lado de um texto. Some sozinho quando a leitura em voz alta está
 * desligada ou o aparelho não tem voz em português — a tela que o usa não precisa saber.
 *
 * O `id` identifica o texto: trocar de texto (ex.: "Próximo" numa matéria) com o botão
 * falando interrompe a leitura anterior, em vez de deixar a voz lendo a página que saiu.
 */
@Component({
  selector: 'app-botao-ouvir',
  standalone: true,
  imports: [NgClass],
  templateUrl: './botao-ouvir.html',
  styleUrl: './botao-ouvir.scss',
})
export class BotaoOuvir {
  protected readonly voz = inject(LeituraVozService);

  readonly texto = input.required<string>();
  readonly id = input<string>(`ouvir-${++proximoId}`);
  readonly rotulo = input('Ouvir');

  protected readonly falando = computed(() => this.voz.falandoId() === this.id());

  constructor() {
    let anterior: string | undefined;
    effect(() => {
      const atual = this.id();
      if (anterior !== undefined && anterior !== atual && this.voz.falandoId() === anterior) {
        this.voz.parar();
      }
      anterior = atual;
    });

    // O texto some da tela (troca de rota, "Voltar"): a voz some junto.
    inject(DestroyRef).onDestroy(() => {
      if (this.voz.falandoId() === this.id()) this.voz.parar();
    });
  }

  protected alternar(): void {
    this.voz.ler(this.id(), this.texto());
  }
}
