import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import {
  DesafioDTO,
  DesafiosService,
  QuizPerguntaDTO,
  QuizResultadoDTO,
} from '../api/desafios.service';

const NIVEL_LABEL: Record<string, string> = { FACIL: 'Fácil', MEDIO: 'Médio', DIFICIL: 'Difícil' };

/**
 * Quiz de um desafio específico. Perguntas são exibidas em lista (mesmo padrão
 * de formulário do resto do sistema, mais simples que um wizard passo a passo).
 * Ao concluir, o placar fica gravado no progresso do aluno — reabrir um desafio
 * já concluído entra direto em modo revisão (gabarito visível, sem interação).
 */
@Component({
  selector: 'app-quiz-desafio',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './quiz-desafio.html',
  styleUrl: './quiz-desafio.scss',
})
export class QuizDesafio implements OnInit {
  private readonly api = inject(DesafiosService);
  private readonly router = inject(Router);

  /** Vem do parâmetro de rota /desafios/:id/quiz (withComponentInputBinding). */
  readonly id = input<string>('');

  readonly carregando = signal(true);
  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly desafio = signal<DesafioDTO | null>(null);
  readonly perguntas = signal<QuizPerguntaDTO[]>([]);
  readonly resultado = signal<QuizResultadoDTO | null>(null);

  /** perguntaId -> índice da alternativa escolhida */
  readonly respostas = signal<Record<number, number>>({});

  readonly nivelLabel = computed(() => {
    const d = this.desafio();
    return d ? (NIVEL_LABEL[d.nivel] ?? d.nivel) : '';
  });

  /** Modo revisão: desafio já concluído (nesta sessão ou antes) — gabarito visível, sem interação. */
  readonly modoRevisao = computed(() => this.resultado() !== null || this.desafio()?.status === 'CONCLUIDO');

  /** Placar exibido no banner: o recém-enviado ou, se reaberto depois, o já persistido. */
  readonly placar = computed(() => {
    const r = this.resultado();
    if (r) return { acertos: r.acertos, total: r.totalPerguntas, xpGanho: r.xpGanho };
    const d = this.desafio();
    return d && d.acertos !== null && d.totalPerguntas !== null
      ? { acertos: d.acertos, total: d.totalPerguntas, xpGanho: 0 }
      : null;
  });

  readonly todasRespondidas = computed(() => {
    const total = this.perguntas().length;
    return total > 0 && Object.keys(this.respostas()).length === total;
  });

  ngOnInit(): void {
    this.carregar();
  }

  private carregar(): void {
    const desafioId = Number(this.id());
    this.carregando.set(true);
    this.api.obter(desafioId).subscribe({
      next: (d) => this.desafio.set(d),
      error: () => this.erro.set('Não foi possível carregar este desafio.'),
    });
    this.api.listarPerguntas(desafioId).subscribe({
      next: (perguntas) => {
        this.perguntas.set(perguntas);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set('Não foi possível carregar as perguntas do quiz.');
        this.carregando.set(false);
      },
    });
  }

  escolher(perguntaId: number, alternativa: number): void {
    if (this.modoRevisao()) return;
    this.respostas.update((atual) => ({ ...atual, [perguntaId]: alternativa }));
  }

  classeAlternativa(pergunta: QuizPerguntaDTO, indice: number): string {
    const escolhida = this.respostas()[pergunta.id];
    if (!this.modoRevisao()) {
      return escolhida === indice ? 'selecionada' : '';
    }
    // Modo revisão: destaca a correta; se o aluno tinha escolhido errado, marca em vermelho.
    if (indice === pergunta.respostaCorreta) return 'correta';
    if (escolhida === indice) return 'incorreta';
    return '';
  }

  enviar(): void {
    if (!this.todasRespondidas()) {
      this.erro.set('Responda todas as perguntas antes de enviar.');
      return;
    }
    this.erro.set('');
    this.enviando.set(true);
    const respostas = Object.entries(this.respostas()).map(([perguntaId, alternativaEscolhida]) => ({
      perguntaId: Number(perguntaId),
      alternativaEscolhida,
    }));
    this.api.finalizarQuiz(Number(this.id()), respostas).subscribe({
      next: (resultado) => {
        this.enviando.set(false);
        this.resultado.set(resultado);
      },
      error: () => {
        this.enviando.set(false);
        this.erro.set('Não foi possível enviar o quiz. Tente novamente.');
      },
    });
  }

  voltar(): void {
    this.router.navigate(['/desafios']);
  }
}
