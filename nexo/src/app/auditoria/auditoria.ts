import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface BarraSemanal {
  dia: string;
  altura: number;
  destaque: boolean;
}

export interface StatAuditoria {
  label: string;
  value: string;
  icon: string;
  color: string;
}

export interface LogAcesso {
  nome: string;
  hora: string;
  status: 'sucesso' | 'perigo';
  avatar: string;
}

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.scss',
})
export class Auditoria {

  stats: StatAuditoria[] = [
    { label: 'Acessos Hoje',        value: '2.847', icon: 'bi-eye',                color: 'blue'   },
    { label: 'Alterações Críticas', value: '18',    icon: 'bi-exclamation-triangle', color: 'orange' },
    { label: 'Conformidade LGPD',   value: '100%',  icon: 'bi-shield-check',         color: 'green'  },
    { label: 'Serviços Ativos',     value: '12/12', icon: 'bi-cpu',                  color: 'purple' },
  ];

  /** Dados do gráfico de barras semanais */
  atividadeSemanal: BarraSemanal[] = [
    { dia: 'Seg', altura: 55, destaque: false },
    { dia: 'Ter', altura: 75, destaque: false },
    { dia: 'Qua', altura: 40, destaque: false },
    { dia: 'Qui', altura: 95, destaque: true  },
    { dia: 'Sex', altura: 65, destaque: false },
  ];

  acessos: LogAcesso[] = [
    {
      nome:   'Prof. Ana Paula Rodrigues',
      status: 'sucesso',
      hora:   '17 Mai 2026 14:23',
      avatar: 'assets/imagensProjeto/gabrielZapelini.png',
    },
    {
      nome:   'Coord. Roberto Silva',
      status: 'sucesso',
      hora:   '17 Mai 2026 14:15',
      avatar: 'assets/imagensProjeto/gabrielZapelini.png',
    },
    {
      nome:   'Tentativa de acesso não autorizada',
      status: 'perigo',
      hora:   '17 Mai 2026 12:47',
      avatar: '',
    },
  ];
}