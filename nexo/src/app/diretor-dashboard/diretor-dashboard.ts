import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts'; // <-- Importações atualizadas
import { ChartConfiguration, ChartOptions, ChartType } from 'chart.js';

@Component({
  selector: 'app-dashboard-diretor',
  standalone: true,
  imports: [CommonModule, BaseChartDirective],
  providers: [
    provideCharts(withDefaultRegisterables()) // <-- ISSO CORRIGE O ERRO DO 'getTheme' REGISTRANDO O CHART.JS CORRETAMENTE
  ],
  templateUrl: './diretor-dashboard.html', 
  styleUrl: './diretor-dashboard.scss'
})
export class DashboardDiretor {

  public lineChartType: ChartType = 'line';
  
  public lineChartData: ChartConfiguration['data'] = {
    labels: ['Jan', 'Fev', 'Mar', 'Abr', 'Mai'],
    datasets: [
      {
        label: 'Taxa Aprovação (%)',
        data: [90, 88, 89, 93, 91],
        borderColor: '#2196f3',
        backgroundColor: 'rgba(33, 150, 243, 0.15)',
        fill: true,
        tension: 0.4,
        pointBackgroundColor: '#2196f3',
        pointHoverRadius: 6
      },
      {
        label: 'Engajamento (%)',
        data: [89, 87, 90, 92, 91],
        borderColor: '#06d25e',
        backgroundColor: 'rgba(6, 210, 94, 0.15)',
        fill: true,
        tension: 0.4,
        pointBackgroundColor: '#06d25e',
        pointHoverRadius: 6
      }
    ]
  };

  public lineChartOptions: ChartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: {
          color: '#8a99ad',
          usePointStyle: true,
          pointStyle: 'circle',
          padding: 20
        }
      },
      tooltip: {
        enabled: true,
        mode: 'index',
        intersect: false,
        backgroundColor: '#1e293b',
        titleColor: '#fff',
        bodyColor: '#fff',
        borderColor: '#334155',
        borderWidth: 1
      }
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        ticks: {
          stepSize: 25,
          color: '#8a99ad'
        },
        grid: {
          color: 'rgba(255, 255, 255, 0.05)'
        }
      },
      x: {
        ticks: {
          color: '#8a99ad'
        },
        grid: {
          display: false
        }
      }
    }
  };

  // Restante dos dados estruturais do Dashboard
  metrics = [
    { label: 'Total de Alunos', value: '1.247', trend: '+5.2%', icon: 'bi-people-fill', color: 'blue' },
    { label: 'Taxa de Evasão', value: '3.8%', trend: '-1.2%', icon: 'bi-person-x-fill', color: 'red' },
    { label: 'Engajamento Médio', value: '87.5%', trend: '+3.1%', icon: 'bi-bullseye', color: 'green' },
    { label: 'Desempenho Geral', value: '8.2', trend: '+0.4', icon: 'bi-graph-up-arrow', color: 'purple' }
  ];

  alunosRisco = [
    { nome: 'Lucas Ferreira', turma: '2º Ano A', frequencia: 45, risco: 'alto', foto: 'https://i.pravatar.cc/150?u=lucas', tempo: '7 dias atrás' },
    { nome: 'Mariana Costa', turma: '1º Ano B', frequencia: 62, risco: 'medio', foto: 'https://i.pravatar.cc/150?u=mariana', tempo: '4 dias atrás' },
    { nome: 'Pedro Souza', turma: '3º Ano C', frequencia: 58, risco: 'medio', foto: 'https://i.pravatar.cc/150?u=pedro', tempo: '5 dias atrás' }
  ];

  alertas = [
    { titulo: 'Documentação Pendente', desc: '15 alunos com documentação incompleta', tempo: 'Há 2 horas', nivel: 'alta' },
    { titulo: 'Correções Atrasadas', desc: '3 professores com correções pendentes há +7 dias', tempo: 'Há 5 horas', nivel: 'media' },
    { titulo: 'Baixa Frequência', desc: '8 alunos sem acesso há mais de 7 dias', tempo: 'Hoje', nivel: 'alta' }
  ];

  eventos = [
    { dia: '10', mes: 'Mai', titulo: 'Reunião de Pais', tipo: 'Reunião' },
    { dia: '12', mes: 'Mai', titulo: 'Conselho de Classe', tipo: 'Conselho' }
  ];
}