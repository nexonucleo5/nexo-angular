import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-monitoramento-docente',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './monitoramento-docente.html',
  styleUrl: './monitoramento-docente.scss'
})
export class MonitoramentoDocente {

  topPerformers = [
    { nome: 'Roberto Alves Lima', materia: 'História', nota: 4.9, foto: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100' },
    { nome: 'Ana Paula Rodrigues', materia: 'Matemática', nota: 4.8, foto: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=100' },
    { nome: 'Juliana Costa Santos', nota: 4.6, materia: 'Biologia', foto: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=100' }
  ];

  professores = [
    {
      nome: 'Prof. Roberto Alves Lima',
      materia: 'História',
      status: 'Excelente',
      statusClasse: 'excelente',
      turmas: '1º A, 2º A, 3º C',
      pendentes: 2,
      tempo: 1.5,
      interacoes: 51,
      avaliacao: 4.9,
      tarefasConcluidas: 51,
      tarefasTotal: 53,
      foto: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150'
    },
    {
      nome: 'Prof. Ana Paula Rodrigues',
      materia: 'Matemática',
      status: 'Excelente',
      statusClasse: 'excelente',
      turmas: '1º A, 1º B, 2º A',
      pendentes: 3,
      tempo: 1.8,
      interacoes: 47,
      avaliacao: 4.8,
      tarefasConcluidas: 47,
      tarefasTotal: 50,
      foto: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150'
    },
    {
      nome: 'Prof. Juliana Costa Santos',
      materia: 'Biologia',
      status: 'Bom',
      statusClasse: 'bom',
      turmas: '1º C, 2º C, 3º B',
      pendentes: 5,
      tempo: 2.1,
      interacoes: 43,
      avaliacao: 4.6,
      tarefasConcluidas: 43,
      tarefasTotal: 48,
      foto: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150'
    },
    {
      nome: 'Prof. Mariana Oliveira',
      materia: 'Química',
      status: 'Bom',
      statusClasse: 'bom',
      turmas: '2º B, 3º B, 3º A',
      pendentes: 6,
      tempo: 2.8,
      interacoes: 41,
      avaliacao: 4.4,
      tarefasConcluidas: 44,
      tarefasTotal: 50,
      foto: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150'
    },
    {
      nome: 'Prof. Carlos Eduardo Silva',
      materia: 'Física',
      status: 'Atenção',
      statusClasse: 'atencao',
      turmas: '2º B, 3º A',
      pendentes: 12,
      tempo: 4.2,
      interacoes: 28,
      avaliacao: 4.2,
      tarefasConcluidas: 38,
      tarefasTotal: 50,
      foto: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150'
    }
  ];
}