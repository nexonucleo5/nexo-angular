import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface AlunoRisco {
  nome: string;
  matricula: string;
  anoTurma: string;
  anoFiltro: string;
  frequencia: number;
  participacao: number;
  notaMedia: number;
  ultimoAcesso: string;
  risco: 'Alto' | 'Médio';
  motivoPrincipal: string;
  intervencoes: string;
  tempoIntervencao: string;
  contatoResponsavel: string;
  emailResponsavel: string;
  foto: string;
}

@Component({
  selector: 'app-gestao-evasao',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './gestao-evasao.html',
  styleUrl: './gestao-evasao.scss',
})
export class GestaoEvasao {
  buscaNome         = '';
  riscoSelecionado  = 'Todos os Riscos';
  turmaSelecionada  = 'Todas as Turmas';

  riscos: string[] = ['Todos os Riscos', 'Risco Alto', 'Risco Médio'];
  turmas: string[] = ['Todas as Turmas', '1º Ano', '2º Ano', '3º Ano'];

  alunos: AlunoRisco[] = [
    {
      nome: 'Lucas Ferreira Silva',
      matricula: '2024001',
      anoTurma: '2º Ano A - Ensino Médio',
      anoFiltro: '2º Ano',
      frequencia: 45,
      participacao: 32,
      notaMedia: 4.5,
      ultimoAcesso: '7 dias atrás',
      risco: 'Alto',
      motivoPrincipal: 'Baixa frequência e desempenho',
      intervencoes: '2 intervenção(ões)',
      tempoIntervencao: 'Há 3 dias',
      contatoResponsavel: '(11) 98765-4321',
      emailResponsavel: 'responsavel.lucas@email.com',
      foto: 'https://images.unsplash.com/photo-1539571696357-5a69c17a67c6?w=150',
    },
    {
      nome: 'Mariana Costa Santos',
      matricula: '2024002',
      anoTurma: '1º Ano B - Ensino Médio',
      anoFiltro: '1º Ano',
      frequencia: 62,
      participacao: 58,
      notaMedia: 6.2,
      ultimoAcesso: '4 dias atrás',
      risco: 'Médio',
      motivoPrincipal: 'Queda no engajamento',
      intervencoes: '1 intervenção(ões)',
      tempoIntervencao: 'Há 1 semana',
      contatoResponsavel: '(11) 97654-3210',
      emailResponsavel: 'responsavel.mariana@email.com',
      foto: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
    },
    {
      nome: 'Pedro Henrique Souza',
      matricula: '2024003',
      anoTurma: '3º Ano C - Ensino Médio',
      anoFiltro: '3º Ano',
      frequencia: 58,
      participacao: 51,
      notaMedia: 5.8,
      ultimoAcesso: '5 dias atrás',
      risco: 'Médio',
      motivoPrincipal: 'Irregular participação',
      intervencoes: '1 intervenção(ões)',
      tempoIntervencao: 'Há 5 dias',
      contatoResponsavel: '(11) 96543-2109',
      emailResponsavel: 'responsavel.pedro@email.com',
      foto: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150',
    },
    {
      nome: 'Julia Beatriz Santos',
      matricula: '2024004',
      anoTurma: '2º Ano B - Ensino Médio',
      anoFiltro: '2º Ano',
      frequencia: 38,
      participacao: 28,
      notaMedia: 4.1,
      ultimoAcesso: '10 dias atrás',
      risco: 'Alto',
      motivoPrincipal: 'Inatividade prolongada',
      intervencoes: '3 intervenção(ões)',
      tempoIntervencao: 'Há 2 dias',
      contatoResponsavel: '(11) 95432-1098',
      emailResponsavel: 'responsavel.julia@email.com',
      foto: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=150',
    },
  ];

  private normalizar(s: string): string {
    return s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
  }

  get alunosFiltrados(): AlunoRisco[] {
    return this.alunos.filter(aluno => {
      const termo = this.normalizar(this.buscaNome);
      const condicaoBusca =
        !termo ||
        this.normalizar(aluno.nome).includes(termo) ||
        aluno.matricula.includes(termo) ||
        this.normalizar(aluno.anoTurma).includes(termo);

      const condicaoRisco =
        this.riscoSelecionado === 'Todos os Riscos' ||
        this.normalizar(this.riscoSelecionado).includes(this.normalizar(aluno.risco));

      const condicaoTurma =
        this.turmaSelecionada === 'Todas as Turmas' ||
        aluno.anoFiltro === this.turmaSelecionada;

      return condicaoBusca && condicaoRisco && condicaoTurma;
    });
  }

  getClasseRisco(risco: string): string {
    return this.normalizar(risco); // 'alto' ou 'medio'
  }
}