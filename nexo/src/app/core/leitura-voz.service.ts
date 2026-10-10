import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { ConfiguracaoAlunoService } from '../configuracao-aluno/configuracao-aluno.service';

/**
 * Teto de caracteres por fala. O Chrome corta em silêncio uma fala longa (~15 s) quando a
 * voz é a de rede, e o texto das matérias tem parágrafos inteiros: em trechos curtos a
 * leitura chega ao fim e ainda dá para parar entre um e outro.
 */
const TAMANHO_MAX_TRECHO = 180;

/**
 * Prepara o texto da tela para ser falado. Emoji e ícone viram "carinha sorrindo" na voz
 * sintética, e quebra de linha vira pausa: sem ponto final, o fim de um parágrafo
 * emendaria no começo do seguinte.
 */
export function limparParaFala(texto: string): string {
  return texto
    .replace(/\p{Extended_Pictographic}/gu, '')
    .replace(/[‍️]/g, '')
    .replace(/([.!?:;])[ \t]*\n+[ \t]*/g, '$1 ')
    .replace(/[ \t]*\n+[ \t]*/g, '. ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Quebra em frases e junta até o teto; frase maior que o teto é cortada em vírgula ou espaço. */
export function dividirEmTrechos(texto: string, max = TAMANHO_MAX_TRECHO): string[] {
  const frases = texto.match(/[^.!?;:]+[.!?;:]*\s*/g) ?? [texto];
  const trechos: string[] = [];
  let atual = '';

  const fechar = () => {
    if (atual.trim()) trechos.push(atual.trim());
    atual = '';
  };

  for (const frase of frases) {
    if (frase.length > max) {
      fechar();
      let resto = frase;
      while (resto.length > max) {
        let corte = resto.lastIndexOf(',', max);
        if (corte < max / 2) corte = resto.lastIndexOf(' ', max);
        if (corte <= 0) corte = max;
        trechos.push(resto.slice(0, corte + 1).trim());
        resto = resto.slice(corte + 1);
      }
      atual = resto;
      continue;
    }
    if (atual.length + frase.length > max) fechar();
    atual += frase;
  }
  fechar();
  return trechos.filter(Boolean);
}

/**
 * Só português serve (ler em inglês é pior que não ler); pt-BR vale mais que pt-PT. Dentro
 * do mesmo idioma, ganha a voz neural: a "Maria Desktop" do Windows e as vozes antigas do
 * sistema soam robóticas, enquanto as "Natural"/"Online" do Edge, as do Google e as
 * "Enhanced"/"Premium" da Apple soam bem mais humanas. O sort é estável, então em empate
 * vale a ordem em que o navegador listou.
 */
export function escolherVozPt(vozes: readonly SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  const pontos = (v: SpeechSynthesisVoice): number => {
    const lang = v.lang.replace('_', '-').toLowerCase();
    if (!lang.startsWith('pt')) return -1;
    let p = lang === 'pt-br' ? 100 : 50;
    if (/natural|neural|online|enhanced|premium|aprimorad/i.test(v.name)) p += 30;
    else if (/google/i.test(v.name)) p += 20;
    if (/desktop/i.test(v.name)) p -= 10;
    return p;
  };
  const melhor = [...vozes].sort((a, b) => pontos(b) - pontos(a))[0];
  return melhor && pontos(melhor) >= 0 ? melhor : null;
}

/**
 * Leitura em voz alta (Configurações → Acessibilidade → "Leitura em voz alta").
 *
 * Usa a síntese de fala do próprio navegador: nada sai do aparelho e não há serviço
 * externo. A voz é a do sistema do aluno, então só se oferece o botão quando existe uma
 * voz em português — ler texto em português com voz inglesa é pior do que não ler.
 */
@Injectable({ providedIn: 'root' })
export class LeituraVozService {
  private readonly config = inject(ConfiguracaoAlunoService);
  private readonly sintese: SpeechSynthesis | null =
    typeof window !== 'undefined' && 'speechSynthesis' in window ? window.speechSynthesis : null;

  private readonly vozes = signal<readonly SpeechSynthesisVoice[]>([]);
  /** Cada fala nova invalida as anteriores: o cancel() delas dispara onend/onerror tardios. */
  private geracao = 0;
  /** O Chrome coleta falas sem referência e nunca dispara o onend delas: guardamos a fila. */
  private fila: SpeechSynthesisUtterance[] = [];

  /** Quem está falando agora (o id do botão), ou null. */
  readonly falandoId = signal<string | null>(null);

  readonly vozPt = computed(() => escolherVozPt(this.vozes()));
  /** O aparelho consegue ler em português. */
  readonly suportado = computed(() => this.sintese !== null && this.vozPt() !== null);
  /** O aluno ligou o recurso e o aparelho dá conta dele: é o que decide mostrar o botão. */
  readonly ativo = computed(
    () => this.config.settings().acessibilidade.leituraVozAlta && this.suportado()
  );
  /** Ligado por ele, mas sem voz em português neste aparelho — a tela de configuração avisa. */
  readonly semVozNoAparelho = computed(
    () => this.config.settings().acessibilidade.leituraVozAlta && !this.suportado()
  );

  constructor() {
    if (!this.sintese) return;

    const carregar = () => this.vozes.set(this.sintese!.getVoices());
    carregar();
    // As vozes chegam de forma assíncrona (a primeira chamada costuma voltar vazia).
    this.sintese.addEventListener?.('voiceschanged', carregar);

    // Desligar o recurso cala quem estiver falando.
    effect(() => {
      if (!this.ativo()) untracked(() => this.parar());
    });

    // Sair da aba ou da página não deixa o aparelho falando sozinho.
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) this.parar();
    });
    window.addEventListener('pagehide', () => this.parar());
  }

  /** Lê o texto; se este mesmo id já está falando, para (o botão é liga/desliga). */
  ler(id: string, texto: string): void {
    if (!this.sintese || !this.ativo()) return;
    if (this.falandoId() === id) {
      this.parar();
      return;
    }
    this.parar();

    const trechos = dividirEmTrechos(limparParaFala(texto));
    if (trechos.length === 0) return;

    const minha = ++this.geracao;
    const voz = this.vozPt()!;
    this.falandoId.set(id);

    // Todos os trechos entram na fila do navegador de uma vez: ele já sintetiza o próximo
    // enquanto fala o atual, sem a pausa de esperar o onend para só então pedir o seguinte.
    this.fila = trechos.map((trecho, i) => {
      const fala = new SpeechSynthesisUtterance(trecho);
      fala.voice = voz;
      fala.lang = voz.lang;
      fala.onend = () => {
        if (minha === this.geracao && i === trechos.length - 1) this.falandoId.set(null);
      };
      fala.onerror = () => {
        // 'interrupted' e 'canceled' são o nosso próprio parar() (a geração já mudou);
        // qualquer outro erro derruba o resto da fila, para o botão não ficar preso em "parar".
        if (minha === this.geracao) this.parar();
      };
      return fala;
    });
    this.fila.forEach((fala) => this.sintese!.speak(fala));
  }

  parar(): void {
    this.geracao++;
    this.fila = [];
    this.sintese?.cancel();
    if (this.falandoId() !== null) this.falandoId.set(null);
  }
}
