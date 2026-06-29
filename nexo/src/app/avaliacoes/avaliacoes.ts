import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface StatAvaliacao {
  label: string;
  value: string;
  icon: string;
  color: string;
}

export interface Avaliacao {
  titulo: string;
  tipo: string;
  turma: string;
  data: string;
  status: 'Em Correção' | 'Aguardando' | 'Concluída';
  corrigidas: number;
  total: number;
}

export interface ItemFilaCorrecao {
  aluno: string;
  avaliacao: string;
  turma: string;
  dataEntrega: string;
  prioridade: 'alta' | 'média' | 'baixa';
}

export interface Questao {
  enunciado: string;
  tipo: 'Objetiva' | 'Discursiva';
  dificuldade: 'Fácil' | 'Médio' | 'Difícil';
  tags: string[];
  utilizada: number;
}

export interface FormNovaAvaliacao {
  titulo: string;
  tipo: string;
  turma: string;
  notaMaxima: number;
  peso: number;
  dataAplicacao: string;
  instrucoes: string;
}

// ── Componente ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-avaliacoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './avaliacoes.html',
  styleUrl: './avaliacoes.scss',
})
export class Avaliacoes {

  abaAtiva   = 'ativas';
  buscaTermo = '';
  mensagemSucesso = '';
  mensagemErro    = '';

  stats: StatAvaliacao[] = [
    { label: 'Avaliações Ativas',      value: '12',  icon: 'bi-journal-text',  color: 'blue'   },
    { label: 'Pendentes de Correção',  value: '23',  icon: 'bi-clock-history', color: 'orange' },
    { label: 'Concluídas no Mês',      value: '45',  icon: 'bi-check2-circle', color: 'green'  },
    { label: 'Média Geral das Turmas', value: '7.8', icon: 'bi-graph-up',      color: 'blue'   },
  ];

  avaliacoes: Avaliacao[] = [
    { titulo: 'Prova Bimestral — Funções',            tipo: 'Prova',    turma: '2º Ano A', data: '15 Jun 2026', status: 'Em Correção', corrigidas: 12, total: 20 },
    { titulo: 'Trabalho em Grupo — Geometria Espacial', tipo: 'Trabalho', turma: '3º Ano B', data: '18 Jun 2026', status: 'Aguardando',  corrigidas: 2,  total: 5  },
    { titulo: 'Quiz Online — Trigonometria',           tipo: 'Quiz',     turma: '1º Ano C', data: '20 Jun 2026', status: 'Concluída',   corrigidas: 15, total: 15 },
  ];

  filaCorrecao: ItemFilaCorrecao[] = [
    { aluno: 'Ana Carolina Silva',  avaliacao: 'Prova Bimestral — Funções',    turma: '2º Ano A', dataEntrega: '10 Jun 2026', prioridade: 'alta'  },
    { aluno: 'Bruno Henrique Costa',avaliacao: 'Prova Bimestral — Funções',    turma: '2º Ano A', dataEntrega: '10 Jun 2026', prioridade: 'alta'  },
    { aluno: 'Camila Rodrigues',    avaliacao: 'Trabalho em Grupo — Geometria', turma: '3º Ano B', dataEntrega: '12 Jun 2026', prioridade: 'média' },
  ];

  bancoQuestoes: Questao[] = [
    { enunciado: 'Calcule o valor de x na equação: 2x + 5 = 15',              tipo: 'Objetiva',  dificuldade: 'Fácil',  tags: ['Equações', 'Primeiro Grau'],     utilizada: 12 },
    { enunciado: 'Explique o conceito de derivada e sua aplicação prática',    tipo: 'Discursiva', dificuldade: 'Difícil', tags: ['Cálculo', 'Derivadas'],          utilizada: 5  },
    { enunciado: 'Resolva o sistema de equações lineares usando matriz',       tipo: 'Discursiva', dificuldade: 'Médio',  tags: ['Sistemas Lineares', 'Matrizes'], utilizada: 8  },
  ];

  // ── Formulário de nova avaliação (model binding) ──────────────────
  novaAvaliacao: FormNovaAvaliacao = {
    titulo:         '',
    tipo:           '',
    turma:          '',
    notaMaxima:     10,
    peso:           3,
    dataAplicacao:  '',
    instrucoes:     '',
  };

  turmasDisponiveis = ['2º Ano A', '3º Ano B', '1º Ano C'];
  tiposAvaliacao    = ['Prova', 'Trabalho', 'Quiz', 'Exercício'];

  // ── Métodos ───────────────────────────────────────────────────────

  setAba(aba: string): void {
    this.abaAtiva = aba;
  }

  get avaliacoesFiltradas(): Avaliacao[] {
    return this.avaliacoes.filter(a =>
      a.titulo.toLowerCase().includes(this.buscaTermo.toLowerCase()) ||
      a.tipo.toLowerCase().includes(this.buscaTermo.toLowerCase())
    );
  }

  getClasseStatus(status: string): string {
    const mapa: Record<string, string> = {
      'Em Correção': 'em-correcao',
      'Aguardando':  'aguardando',
      'Concluída':   'concluida',
    };
    return mapa[status] ?? 'aguardando';
  }

  criarAvaliacao(): void {
    if (!this.novaAvaliacao.titulo.trim()) {
      this.mensagemErro = 'O título da avaliação é obrigatório.';
      return;
    }
    if (!this.novaAvaliacao.tipo) {
      this.mensagemErro = 'Selecione o tipo de avaliação.';
      return;
    }
    if (!this.novaAvaliacao.turma) {
      this.mensagemErro = 'Selecione a turma.';
      return;
    }

    // TODO: integrar com API — this.avaliacaoService.criar(this.novaAvaliacao)
    console.log('Nova avaliação para envio ao backend:', this.novaAvaliacao);

    this.avaliacoes.push({
      titulo:     this.novaAvaliacao.titulo,
      tipo:       this.novaAvaliacao.tipo,
      turma:      this.novaAvaliacao.turma,
      data:       this.novaAvaliacao.dataAplicacao || '—',
      status:     'Aguardando',
      corrigidas: 0,
      total:      0,
    });

    this.mensagemErro    = '';
    this.mensagemSucesso = '✅ Avaliação criada com sucesso!';
    this.novaAvaliacao   = { titulo: '', tipo: '', turma: '', notaMaxima: 10, peso: 3, dataAplicacao: '', instrucoes: '' };

    setTimeout(() => {
      this.mensagemSucesso = '';
      this.setAba('ativas');
    }, 2000);
  }

  salvarRascunho(): void {
    console.log('Rascunho salvo:', this.novaAvaliacao);
    this.mensagemSucesso = '💾 Rascunho salvo!';
    setTimeout(() => (this.mensagemSucesso = ''), 2000);
  }
}