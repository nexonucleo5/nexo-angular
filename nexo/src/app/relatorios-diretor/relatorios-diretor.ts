import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-relatorios-diretor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './relatorios-diretor.html',
  styleUrl: './relatorios-diretor.scss'
})
export class RelatoriosDiretor {

  stats = [
    { label: 'Taxa de Aprovação', value: '91.2%', icon: 'bi-graph-up', color: 'green', trend: '+2.3%' },
    { label: 'NPS Institucional', value: '8.7', icon: 'bi-star', color: 'orange', trend: '+0.5' },
    { label: 'Egressos Certificados', value: '342', icon: 'bi-people', color: 'blue', trend: '+18' },
    { label: 'Ouvidoria Resolvida', value: '94%', icon: 'bi-check-circle', color: 'purple', trend: '+3%' }
  ];

  // Dados para o gráfico de colunas
  disciplinasDesempenho = [
    { nome: 'Mat', valor: 85, status: 'normal' },
    { nome: 'Port', valor: 92, status: 'normal' },
    { nome: 'Fís', valor: 58, status: 'critico' },
    { nome: 'Quím', valor: 78, status: 'normal' },
    { nome: 'Bio', valor: 88, status: 'normal' },
    { nome: 'Hist', valor: 90, status: 'normal' }
  ];

  // Lista inferior
  gargalos = [
    { disciplina: 'Física', professor: 'Prof. Carlos Eduardo', taxa: '15%', nivel: 'Alta' },
    { disciplina: 'Química', professor: 'Profa. Mariana Oliveira', taxa: '13%', nivel: 'Média' },
    { disciplina: 'Matemática', professor: 'Profa. Ana Paula', taxa: '12%', nivel: 'Média' }
  ];
}