import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { DesafiosService, QuizPerguntaDTO } from '../api/desafios.service';
import { QuizDesafio } from './quiz-desafio';

const pergunta = (respostaCorreta: number | null): QuizPerguntaDTO => ({
  id: 1,
  enunciado: 'Quanto é 2 + 2?',
  alternativas: ['3', '4', '5'],
  respostaCorreta,
});

describe('QuizDesafio — o que a voz lê', () => {
  function montar(): QuizDesafio {
    TestBed.configureTestingModule({
      providers: [
        { provide: DesafiosService, useValue: {} },
        { provide: Router, useValue: {} },
      ],
    });
    return TestBed.runInInjectionContext(() => new QuizDesafio());
  }

  it('lê o enunciado e as alternativas com a letra, na ordem da tela', () => {
    const fala = montar().falaDaPergunta(pergunta(null), 0);

    expect(fala).toBe('Pergunta 1. Quanto é 2 + 2?\nAlternativa A: 3\nAlternativa B: 4\nAlternativa C: 5');
  });

  it('sem gabarito liberado pelo servidor, a voz não entrega a resposta', () => {
    expect(montar().falaDaPergunta(pergunta(null), 0)).not.toContain('Resposta correta');
  });

  it('no modo revisão (gabarito liberado), a voz diz qual era a resposta correta', () => {
    const fala = montar().falaDaPergunta(pergunta(1), 0);

    expect(fala.endsWith('Resposta correta: Alternativa B.')).toBe(true);
  });

  it('o aviso de tentativa reprovada não revela o que errou', () => {
    const fala = montar().falaDaTentativa();

    expect(fala).toContain('Ainda não foi dessa vez.');
    expect(fala).toContain('Nenhum XP foi creditado.');
    expect(fala).not.toMatch(/Resposta correta|Alternativa/);
  });

  it('o placar fala o XP creditado, ou que o desafio já estava concluído', () => {
    const quiz = montar();

    expect(quiz.falaDoPlacar({ acertos: 5, total: 5, xpGanho: 50 })).toContain('Mais 50 XP creditado');
    expect(quiz.falaDoPlacar({ acertos: 5, total: 5, xpGanho: 0 })).toContain('já concluído anteriormente');
  });
});
