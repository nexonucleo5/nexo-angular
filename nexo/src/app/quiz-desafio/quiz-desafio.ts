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
 *
 * Regra do desafio: só conclui quem acerta tudo.
 * - Gabaritou  → banner de parabéns, XP creditado, quiz vira modo revisão com o
 *   gabarito visível (o backend só devolve `respostaCorreta` depois de CONCLUIDO).
 * - Errou algo → banner "tente novamente", nenhum XP, seleções limpas e nada
 *   revelado: nem quais perguntas errou, nem qual era a alternativa correta.
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

  /** Última tentativa enviada foi reprovada — mostra o aviso sem revelar nada. */
  readonly tentativaReprovada = signal(false);

  /** Tentativas já enviadas. Vem do backend, então sobrevive a recarregar a página. */
  readonly tentativas = signal(0);

  /** perguntaId -> índice da alternativa escolhida */
  readonly respostas = signal<Record<number, number>>({});

  readonly nivelLabel = computed(() => {
    const d = this.desafio();
    return d ? (NIVEL_LABEL[d.nivel] ?? d.nivel) : '';
  });

  /** Modo revisão: só para quem gabaritou — gabarito visível, sem interação. */
  readonly modoRevisao = computed(
    () => this.resultado()?.aprovado === true || this.desafio()?.status === 'CONCLUIDO',
  );

  /** Placar do banner de sucesso: o recém-enviado ou, se reaberto depois, o já persistido. */
  readonly placar = computed(() => {
    const r = this.resultado();
    if (r?.aprovado) {
      return { acertos: r.acertos ?? r.totalPerguntas, total: r.totalPerguntas, xpGanho: r.xpGanho };
    }
    const d = this.desafio();
    return d && d.status === 'CONCLUIDO' && d.acertos !== null && d.totalPerguntas !== null
      ? { acertos: d.acertos, total: d.totalPerguntas, xpGanho: 0 }
      : null;
  });

  readonly textoTentativas = computed(() =>
    this.tentativas() === 1 ? '1 tentativa' : `${this.tentativas()} tentativas`,
  );

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
      next: (d) => {
        this.desafio.set(d);
        this.tentativas.set(d.tentativas ?? 0);
      },
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
    // Ainda respondendo: só destaca a escolha.
    if (!this.modoRevisao()) {
      return escolhida === indice ? 'selecionada' : '';
    }
    // Modo revisão só existe para quem gabaritou, então tudo que for marcado é verde.
    // Se o gabarito ainda não voltou do servidor, a própria escolha do aluno já é a
    // resposta certa — pinta verde na hora, sem esperar o request de confirmação.
    if (pergunta.respostaCorreta === null) {
      return escolhida === indice ? 'correta' : '';
    }
    return indice === pergunta.respostaCorreta ? 'correta' : '';
  }

  /**
   * `aprovado` é a fonte de verdade, mas um backend desatualizado (ainda sem esse
   * campo no JSON) devolveria `undefined` — e tratar isso como reprovado apagaria
   * as respostas de quem acertou tudo. Nesse caso caímos no placar, que existe nas
   * duas versões do DTO.
   */
  private foiAprovado(r: QuizResultadoDTO): boolean {
    if (typeof r.aprovado === 'boolean') return r.aprovado;
    return r.acertos !== null && r.acertos === r.totalPerguntas;
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
        this.tentativas.set(resultado.tentativas ?? this.tentativas() + 1);

        if (!this.foiAprovado(resultado)) {
          // Reprovado: devolve o quiz em branco, sem pista nenhuma. O desafio
          // continua PROGRESSO no backend, então o gabarito segue escondido.
          this.respostas.set({});
          this.tentativaReprovada.set(true);
          this.aoTopo();
          return;
        }

        // Aprovado: o banner verde e os checks aparecem já nesta tela — as
        // respostas escolhidas continuam marcadas e viram verde na hora.
        this.tentativaReprovada.set(false);
        this.resultado.set({ ...resultado, aprovado: true });
        // Confirmação: recarrega as perguntas para receber o gabarito oficial.
        // Se falhar, a tela continua correta pelas respostas do próprio aluno.
        this.api.listarPerguntas(Number(this.id())).subscribe({
          next: (perguntas) => this.perguntas.set(perguntas),
          error: () => {},
        });
        this.aoTopo();
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

  private aoTopo(): void {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
