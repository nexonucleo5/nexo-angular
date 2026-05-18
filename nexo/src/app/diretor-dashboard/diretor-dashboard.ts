import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboard-diretor',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './diretor-dashboard.html', 
  styleUrl: './diretor-dashboard.scss'
})
export class DashboardDiretor {
  metrics = [
    { label: 'Total de Alunos', value: '1,247', trend: '+5.2%', icon: 'bi-people-fill', color: 'blue' },
    { label: 'Taxa de Evasão', value: '3.8%', trend: '-1.2%', icon: 'bi-person-x-fill', color: 'red' },
    { label: 'Engajamento Médio', value: '87.5%', trend: '+3.1%', icon: 'bi-bullseye', color: 'green' },
    { label: 'Desempenho Geral', value: '8.2', trend: '+0.4', icon: 'bi-graph-up-arrow', color: 'orange' }
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