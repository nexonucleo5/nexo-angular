import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

export interface StatProfessor {
  label: string;
  value: string;
  icon: string;
  color: string;
  tendencia?: string;
}

export interface ProximaAula {
  turma: string;
  disciplina: string;
  hora: string;
  data: string;
  sala: string;
  alunos: number;
}

export interface TurmaProfessor {
  nome: string;
  disciplina: string;
  alunos: number;
  mediaGeral: number;
  progresso: number;
  corProgresso: string;
}

export interface AlunoAtencao {
  nome: string;
  turma: string;
  mediaAtual: number;
  frequencia: number;
  status: 'critico' | 'atencao';
  foto: string;
  ultimaAtividade: string;
}

export interface AtividadeRecente {
  tipo: 'correcao' | 'avaliacao' | 'aviso' | 'diario';
  descricao: string;
  turma: string;
  tempo: string;
  icone: string;
  corIcone: string;
}

@Component({
  selector: 'app-dashboard-professor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-professor.html',
  styleUrl: './dashboard-professor.scss',
})
export class DashboardProfessor {

  // ── KPI Cards ────────────────────────────────────────────────────────
  stats: StatProfessor[] = [
    { label: 'Turmas Ativas',          value: '5',    icon: 'bi-people-fill',      color: 'blue',   tendencia: '+1 este mês' },
    { label: 'Total de Alunos',        value: '142',  icon: 'bi-person-check-fill', color: 'green',  tendencia: 'em 5 turmas'  },
    { label: 'Correções Pendentes',    value: '23',   icon: 'bi-clock-history',    color: 'orange', tendencia: '8 urgentes'  },
    { label: 'Avaliações Este Mês',    value: '8',    icon: 'bi-journal-check',    color: 'purple', tendencia: '3 concluídas' },
  ];

  // ── Próximas Aulas ───────────────────────────────────────────────────
  proximasAulas: ProximaAula[] = [
    { turma: '2º Ano A', disciplina: 'Matemática', hora: '14:00', data: 'Hoje',    sala: '204', alunos: 32 },
    { turma: '3º Ano B', disciplina: 'Física',     hora: '15:50', data: 'Hoje',    sala: '101', alunos: 28 },
    { turma: '1º Ano C', disciplina: 'Matemática', hora: '08:00', data: 'Amanhã', sala: '205', alunos: 30 },
    { turma: '2º Ano B', disciplina: 'Geometria',  hora: '10:00', data: 'Amanhã', sala: '202', alunos: 29 },
  ];

  // ── Minhas Turmas ────────────────────────────────────────────────────
  turmas: TurmaProfessor[] = [
    { nome: '1º Ano A', disciplina: 'Matemática', alunos: 28, mediaGeral: 7.9, progresso: 60, corProgresso: 'blue-fill'   },
    { nome: '2º Ano A', disciplina: 'Matemática', alunos: 32, mediaGeral: 7.4, progresso: 65, corProgresso: 'purple-fill' },
    { nome: '2º Ano B', disciplina: 'Geometria',  alunos: 29, mediaGeral: 8.1, progresso: 70, corProgresso: 'green-fill'  },
    { nome: '3º Ano B', disciplina: 'Física',     alunos: 28, mediaGeral: 7.2, progresso: 55, corProgresso: 'orange-fill' },
    { nome: '1º Ano C', disciplina: 'Matemática', alunos: 25, mediaGeral: 8.5, progresso: 58, corProgresso: 'blue-fill'   },
  ];

  // ── Alunos que precisam de atenção ──────────────────────────────────
  alunosAtencao: AlunoAtencao[] = [
    {
      nome: 'Lucas Ferreira',
      turma: '2º Ano A',
      mediaAtual: 4.5,
      frequencia: 65,
      status: 'critico',
      foto: 'https://i.pravatar.cc/150?u=lucas',
      ultimaAtividade: '10 dias atrás',
    },
    {
      nome: 'Julia Santos',
      turma: '3º Ano B',
      mediaAtual: 5.2,
      frequencia: 72,
      status: 'atencao',
      foto: 'https://i.pravatar.cc/150?u=julia',
      ultimaAtividade: '5 dias atrás',
    },
    {
      nome: 'Marcos Pereira',
      turma: '1º Ano A',
      mediaAtual: 5.8,
      frequencia: 78,
      status: 'atencao',
      foto: 'https://i.pravatar.cc/150?u=marcos',
      ultimaAtividade: '3 dias atrás',
    },
  ];

  // ── Atividades Recentes ──────────────────────────────────────────────
  atividadesRecentes: AtividadeRecente[] = [
    { tipo: 'correcao',  descricao: 'Prova Bimestral - 2º Ano A corrigida', turma: '2º Ano A', tempo: 'Há 2 horas',  icone: 'bi-pencil-square', corIcone: 'text-purple'  },
    { tipo: 'aviso',     descricao: 'Aviso publicado: Entrega de Trabalho',  turma: '3º Ano B', tempo: 'Há 3 horas',  icone: 'bi-megaphone',     corIcone: 'text-blue'    },
    { tipo: 'diario',    descricao: 'Frequência registrada',                 turma: '1º Ano C', tempo: 'Há 5 horas',  icone: 'bi-journal-check', corIcone: 'text-green'   },
    { tipo: 'avaliacao', descricao: 'Quiz de Trigonometria criado',          turma: '2º Ano B', tempo: 'Ontem 15:30', icone: 'bi-file-earmark-plus', corIcone: 'text-orange' },
  ];

  /** Retorna a cor do badge de frequência */
  getClasseFrequencia(freq: number): string {
    if (freq >= 85) return 'text-success';
    if (freq >= 70) return 'text-warning';
    return 'text-danger';
  }
}