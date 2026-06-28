import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface StatRelatorio {
  label: string;
  value: string;
  icon: string;
  color: string;
  trend: string;
}

export interface DisciplinaDesempenho {
  nome: string;
  valor: number;
  status: 'normal' | 'critico';
}

export interface Gargalo {
  disciplina: string;
  professor: string;
  taxa: string;
  nivel: 'Alta' | 'Média';
}

@Component({
  selector: 'app-relatorios-diretor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './relatorios-diretor.html',
  styleUrl: './relatorios-diretor.scss',
})
export class RelatoriosDiretor {

  // ── Filtros ────────────────────────────────────────────────────────
  periodoSelecionado = 'Este Bimestre';
  visaoSelecionada   = 'Visão Geral';

  periodos = ['Este Bimestre', 'Este Semestre', 'Geral'];
  visoes   = ['Visão Geral', 'Pedagógico', 'Financeiro'];

  // ── KPIs ───────────────────────────────────────────────────────────
  stats: StatRelatorio[] = [
    { label: 'Taxa de Aprovação',   value: '91.2%', icon: 'bi-graph-up',    color: 'green',  trend: '+2.3%' },
    { label: 'NPS Institucional',   value: '8.7',   icon: 'bi-star',        color: 'orange', trend: '+0.5'  },
    { label: 'Egressos Certificados', value: '342', icon: 'bi-people',      color: 'blue',   trend: '+18'   },
    { label: 'Ouvidoria Resolvida', value: '94%',   icon: 'bi-check-circle', color: 'purple', trend: '+3%'  },
  ];

  // ── Gráfico de barras ──────────────────────────────────────────────
  disciplinasDesempenho: DisciplinaDesempenho[] = [
    { nome: 'Mat',  valor: 85, status: 'normal'  },
    { nome: 'Port', valor: 92, status: 'normal'  },
    { nome: 'Fís',  valor: 58, status: 'critico' },
    { nome: 'Quím', valor: 78, status: 'normal'  },
    { nome: 'Bio',  valor: 88, status: 'normal'  },
    { nome: 'Hist', valor: 90, status: 'normal'  },
  ];

  // ── Disciplinas gargalo ────────────────────────────────────────────
  gargalos: Gargalo[] = [
    { disciplina: 'Física',      professor: 'Prof. Carlos Eduardo',   taxa: '15%', nivel: 'Alta'  },
    { disciplina: 'Química',     professor: 'Profa. Mariana Oliveira', taxa: '13%', nivel: 'Média' },
    { disciplina: 'Matemática',  professor: 'Profa. Ana Paula',        taxa: '12%', nivel: 'Média' },
  ];
}