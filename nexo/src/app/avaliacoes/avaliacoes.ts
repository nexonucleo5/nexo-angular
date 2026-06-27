import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-avaliacoes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './avaliacoes.html',
  styleUrl: './avaliacoes.scss'
})
export class Avaliacoes {
  abaAtiva: string = 'ativas';
  buscaTermo: string = '';

  stats = [
    { label: 'Avaliações Ativas', value: '12', icon: 'bi-journal-text', color: 'blue' },
    { label: 'Pendentes de Correção', value: '23', icon: 'bi-clock-history', color: 'orange' },
    { label: 'Concluídas no Mês', value: '45', icon: 'bi-check2-circle', color: 'green' },
    { label: 'Média Geral', value: '7.8', icon: 'bi-graph-up', color: 'blue' }
  ];

  avaliacoes = [
    { titulo: 'Prova Bimestral - Funções', tipo: 'Prova', turma: '2º Ano A', data: '15 Jun 2026', status: 'Em Correção', corrigidas: 12, total: 20 },
    { titulo: 'Trabalho em Grupo - Geometria Espacial', tipo: 'Trabalho', turma: '3º Ano B', data: '18 Jun 2026', status: 'Aguardando', corrigidas: 2, total: 5 },
    { titulo: 'Quiz Online - Trigonometria', tipo: 'Quiz', turma: '1º Ano C', data: '20 Jun 2026', status: 'Concluída', corrigidas: 15, total: 15 }
  ];

  filaCorrecao = [
    { aluno: 'Ana Carolina Silva', avaliacao: 'Prova Bimestral - Funções', turma: '2º Ano A', dataEntrega: '10 Jun 2026', prioridade: 'alta' },
    { aluno: 'Bruno Henrique Costa', avaliacao: 'Prova Bimestral - Funções', turma: '2º Ano A', dataEntrega: '10 Jun 2026', prioridade: 'alta' },
    { aluno: 'Camila Rodrigues', avaliacao: 'Trabalho em Grupo - Geometria', turma: '3º Ano B', dataEntrega: '12 Jun 2026', prioridade: 'média' }
  ];

  bancoQuestoes = [
    { enunciado: 'Calcule o valor de x na equação: 2x + 5 = 15', tipo: 'Objetiva', dificuldade: 'Fácil', tags: ['Equações', 'Primeiro Grau'], utilizada: 12 },
    { enunciado: 'Explique o conceito de derivada e sua aplicação prática', tipo: 'Discursiva', dificuldade: 'Difícil', tags: ['Cálculo', 'Derivadas'], utilizada: 5 },
    { enunciado: 'Resolva o sistema de equações lineares usando matriz', tipo: 'Discursiva', dificuldade: 'Médio', tags: ['Sistemas Lineares', 'Matrizes'], utilizada: 8 }
  ];

  setAba(aba: string) {
    this.abaAtiva = aba;
  }

  get avaliacoesFiltradas() {
    return this.avaliacoes.filter(a => 
      a.titulo.toLowerCase().includes(this.buscaTermo.toLowerCase()) ||
      a.tipo.toLowerCase().includes(this.buscaTermo.toLowerCase())
    );
  }

  getClasseStatus(status: string): string {
    return status.toLowerCase().replace(' ', '-').normalize('NFD').replace(/[\u0300-\u036f]/g, "");
  }
}