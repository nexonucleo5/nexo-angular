import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface StatNivel {
  icone: string;
  classeIcone: string;
  label: string;
  valor: string;
}

export interface PontoGrafico {
  x: number;
  y: number;
  label: string;
}

export interface MateriaNota {
  nome: string;
  percentual: number;
  nota: number;
  classeProgresso: string;
}

export interface AtividadeRecente {
  titulo: string;
  materia: string;
  data: string;
  statusEmoji: string;
  statusClasse: string;
  nota: number;
}

@Component({
  selector: 'app-meus-niveis-notas',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './meus-niveis-notas.html',
  styleUrl: './meus-niveis-notas.scss',
})
export class MeusNiveisNotas {

  // ── Dados de nível ─────────────────────────────────────────────────
  nivel = {
    atual:        12,
    titulo:       'Estudante Dedicado',
    xpAtual:      3450,
    xpTotal:      4000,
    proximoNivel: 13,
  };

  get xpPercentual(): number {
    return Math.round((this.nivel.xpAtual / this.nivel.xpTotal) * 100);
  }

  get xpFaltam(): number {
    return this.nivel.xpTotal - this.nivel.xpAtual;
  }

  // ── Cards de estatísticas ───────────────────────────────────────────
  stats: StatNivel[] = [
    { icone: '📊', classeIcone: 'roxo',    label: 'Média Geral', valor: '8.1'  },
    { icone: '📈', classeIcone: 'verde',   label: 'Melhor Nota', valor: '10.0' },
    { icone: '🎖️', classeIcone: 'azul',   label: 'Atividades',  valor: '42'   },
    { icone: '📅', classeIcone: 'laranja', label: 'Este Mês',    valor: '12'   },
  ];

  // ── Dados dos gráficos SVG ─────────────────────────────────────────
  notasHistorico: PontoGrafico[] = [
    { x: 20,  y: 150, label: 'Jan' },
    { x: 120, y: 130, label: 'Fev' },
    { x: 220, y: 110, label: 'Mar' },
    { x: 320, y: 120, label: 'Abr' },
  ];

  xpHistorico: PontoGrafico[] = [
    { x: 20,  y: 160, label: 'Jan' },
    { x: 120, y: 130, label: 'Fev' },
    { x: 220, y: 100, label: 'Mar' },
    { x: 320, y: 75,  label: 'Abr' },
  ];

  get notasPolyline(): string {
    return this.notasHistorico.map(p => `${p.x},${p.y}`).join(' ');
  }

  get xpPolyline(): string {
    return this.xpHistorico.map(p => `${p.x},${p.y}`).join(' ');
  }

  // ── Matérias ────────────────────────────────────────────────────────
  materias: MateriaNota[] = [
    { nome: 'Biologia',   percentual: 75, nota: 8.5, classeProgresso: 'bio' },
    { nome: 'Matemática', percentual: 60, nota: 7.2, classeProgresso: 'mat' },
    { nome: 'História',   percentual: 85, nota: 9.0, classeProgresso: 'his' },
    { nome: 'Inglês',     percentual: 70, nota: 8.0, classeProgresso: 'ing' },
  ];

  // ── Atividades recentes ─────────────────────────────────────────────
  atividades: AtividadeRecente[] = [
    { titulo: 'Quiz: Fotossíntese',     materia: 'Biologia',   data: '30 Mar 2026', statusEmoji: '😊', statusClasse: 'bom',    nota: 9.5  },
    { titulo: 'Desafio: Funções',       materia: 'Matemática', data: '28 Mar 2026', statusEmoji: '😐', statusClasse: 'medio',  nota: 7.0  },
    { titulo: 'Segunda Guerra Mundial', materia: 'História',   data: '25 Mar 2026', statusEmoji: '😊', statusClasse: 'bom',    nota: 10.0 },
    { titulo: 'Present Perfect',        materia: 'Inglês',     data: '22 Mar 2026', statusEmoji: '😊', statusClasse: 'bom',    nota: 8.5  },
    { titulo: 'Lei de Ohm',             materia: 'Física',     data: '20 Mar 2026', statusEmoji: '😮', statusClasse: 'alerta', nota: 6.5  },
  ];
}