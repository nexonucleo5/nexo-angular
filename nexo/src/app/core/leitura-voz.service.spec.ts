import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  LeituraVozService,
  dividirEmTrechos,
  escolherVozPt,
  limparParaFala,
} from './leitura-voz.service';
import { ConfiguracaoAlunoService } from '../configuracao-aluno/configuracao-aluno.service';

const voz = (lang: string, name = lang) => ({ lang, name }) as SpeechSynthesisVoice;

describe('preparo do texto para a voz', () => {
  it('tira emoji, que a voz sintética leria como "carinha sorrindo"', () => {
    expect(limparParaFala('Parabéns 🎉 você acertou 👏')).toBe('Parabéns você acertou');
  });

  it('quebra de linha vira pausa, sem pontuação duplicada', () => {
    expect(limparParaFala('Primeiro parágrafo\nSegundo parágrafo.\nTerceiro')).toBe(
      'Primeiro parágrafo. Segundo parágrafo. Terceiro'
    );
  });
});

describe('divisão em trechos', () => {
  it('texto curto fica inteiro', () => {
    expect(dividirEmTrechos('Uma frase. Outra frase.')).toEqual(['Uma frase. Outra frase.']);
  });

  it('nenhum trecho passa do teto e nada do texto se perde', () => {
    const texto = Array.from({ length: 30 }, (_, i) => `Esta é a frase número ${i}.`).join(' ');
    const trechos = dividirEmTrechos(texto, 100);

    expect(trechos.length).toBeGreaterThan(1);
    expect(trechos.every((t) => t.length <= 100)).toBe(true);
    expect(trechos.join(' ').replace(/\s+/g, ' ')).toBe(texto);
  });

  it('frase maior que o teto, sem ponto, é cortada em vírgula ou espaço', () => {
    const frase = 'palavra, '.repeat(60).trim();
    const trechos = dividirEmTrechos(frase, 80);

    expect(trechos.length).toBeGreaterThan(1);
    expect(trechos.every((t) => t.length <= 80)).toBe(true);
  });
});

describe('escolha da voz', () => {
  it('prefere pt-BR a outro português e ignora outros idiomas', () => {
    const vozes = [voz('en-US'), voz('pt-PT'), voz('pt_BR')];
    expect(escolherVozPt(vozes)?.lang).toBe('pt_BR');
  });

  it('aceita outro português quando não há pt-BR', () => {
    expect(escolherVozPt([voz('en-US'), voz('pt-PT')])?.lang).toBe('pt-PT');
  });

  it('sem português, nenhuma voz — melhor não ler do que ler com sotaque errado', () => {
    expect(escolherVozPt([voz('en-US'), voz('es-ES')])).toBeNull();
  });
});

class FalaFalsa {
  voice: SpeechSynthesisVoice | null = null;
  lang = '';
  onend: (() => void) | null = null;
  onerror: (() => void) | null = null;
  constructor(public text: string) {}
}

describe('LeituraVozService', () => {
  let ligado: ReturnType<typeof signal<boolean>>;
  let faladas: FalaFalsa[];
  let sintese: { speak: ReturnType<typeof vi.fn>; cancel: ReturnType<typeof vi.fn> };

  function montar(vozes: SpeechSynthesisVoice[], leituraVozAlta = true): LeituraVozService {
    faladas = [];
    sintese = {
      speak: vi.fn((f: FalaFalsa) => faladas.push(f)),
      cancel: vi.fn(),
    };
    Object.defineProperty(window, 'speechSynthesis', {
      configurable: true,
      value: { ...sintese, getVoices: () => vozes, addEventListener: () => {} },
    });
    (globalThis as any).SpeechSynthesisUtterance = FalaFalsa;

    ligado = signal(leituraVozAlta);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: ConfiguracaoAlunoService,
          useValue: { settings: () => ({ acessibilidade: { leituraVozAlta: ligado() } }) },
        },
      ],
    });
    return TestBed.inject(LeituraVozService);
  }

  afterEach(() => {
    delete (window as any).speechSynthesis;
    delete (globalThis as any).SpeechSynthesisUtterance;
  });

  it('só fica ativo com o recurso ligado E uma voz em português no aparelho', () => {
    expect(montar([voz('pt-BR')]).ativo()).toBe(true);
  });

  it('com o recurso ligado mas sem voz em português, avisa em vez de oferecer o botão', () => {
    const svc = montar([voz('en-US')]);

    expect(svc.ativo()).toBe(false);
    expect(svc.semVozNoAparelho()).toBe(true);
  });

  it('com o recurso desligado não lê nada', () => {
    const svc = montar([voz('pt-BR')], false);

    svc.ler('a', 'Olá, mundo.');

    expect(svc.ativo()).toBe(false);
    expect(faladas).toHaveLength(0);
  });

  it('lê os trechos em sequência, na voz portuguesa, e termina sozinho', () => {
    const svc = montar([voz('en-US'), voz('pt-BR')]);
    const longo = Array.from({ length: 20 }, (_, i) => `Frase número ${i} do texto.`).join(' ');

    svc.ler('a', longo);

    expect(svc.falandoId()).toBe('a');
    expect(faladas).toHaveLength(1); // um por vez: o próximo só depois do onend
    expect(faladas[0].lang).toBe('pt-BR');

    while (faladas.at(-1)!.onend && svc.falandoId() === 'a') {
      faladas.at(-1)!.onend!();
    }
    expect(faladas.length).toBeGreaterThan(1);
    expect(svc.falandoId()).toBeNull();
  });

  it('clicar de novo no mesmo texto para a leitura', () => {
    const svc = montar([voz('pt-BR')]);

    svc.ler('a', 'Um texto.');
    svc.ler('a', 'Um texto.');

    expect(svc.falandoId()).toBeNull();
    expect(sintese.cancel).toHaveBeenCalled();
  });

  it('começar outro texto interrompe o primeiro', () => {
    const svc = montar([voz('pt-BR')]);

    svc.ler('a', 'Primeiro.');
    svc.ler('b', 'Segundo.');

    expect(svc.falandoId()).toBe('b');
    expect(faladas.at(-1)!.text).toBe('Segundo.');
  });

  it('o fim tardio de uma fala já interrompida não religa nem apaga a leitura nova', () => {
    const svc = montar([voz('pt-BR')]);
    svc.ler('a', 'Primeiro texto. Com duas frases.');
    const antiga = faladas[0];

    svc.ler('b', 'Segundo texto.');
    const quantas = faladas.length;
    antiga.onend!(); // o navegador avisa o fim da fala cancelada, atrasado

    expect(svc.falandoId()).toBe('b');
    expect(faladas).toHaveLength(quantas);
  });

  it('desligar o recurso cala quem estiver falando', () => {
    const svc = montar([voz('pt-BR')]);
    svc.ler('a', 'Um texto.');

    ligado.set(false);
    TestBed.tick();

    expect(svc.falandoId()).toBeNull();
    expect(sintese.cancel).toHaveBeenCalled();
  });
});
