import { Component } from '@angular/core';
import { CommonModule } from '@angular/common'; // ESSENCIAL para *ngFor e *ngIf funcionarem

@Component({
  selector: 'app-dashboards',
  standalone: true,
  imports: [CommonModule], // Adicionado aqui
  templateUrl: './dashboards.html',
  styleUrl: './dashboards.scss',
})
export class Dashboards {

  // Dados do Ranking (Baseado na imagem do Figma)
  ranking = [
    { name: 'Ana Costa', xp: 4520, pos: 1, photo: 'assets/ana.jpg', rankColor: 'gold', isMe: false },
    { name: 'Carlos Santos', xp: 3980, pos: 2, photo: 'assets/carlos.jpg', rankColor: 'silver', isMe: false },
    { name: 'Gabriel Silva', xp: 3450, pos: 3, photo: 'assets/gabriel.jpg', rankColor: 'bronze', isMe: true }
  ];

  // Dados dos Cards Superiores
  stats = [
    { label: 'Tempo de Estudo Hoje', value: '2h 45min', icon: 'bi-clock', colorClass: 'purple-bg' },
    { label: 'Desafios Concluídos', value: '8/12', icon: 'bi-trophy', colorClass: 'green-bg' },
    { label: 'XP Acumulado', value: '3,450 XP', icon: 'bi-lightning-charge', colorClass: 'orange-bg' },
    { label: 'Posição no Ranking', value: '#3', icon: 'bi-award', colorClass: 'blue-bg' }
  ];

  // Dados das Atividades Recentes (Faltava isso!)
  recentActivities = [
    { name: 'Genética - DNA e RNA', subject: 'Biologia', time: 'Há 2 horas', progress: 100 },
    { name: 'Equações do 2º Grau', subject: 'Matemática', time: 'Há 5 horas', progress: 75 },
    { name: 'Segunda Guerra Mundial', subject: 'História', time: 'Ontem', progress: 100 }
  ];

  // Dados dos Próximos Desafios
  challenges = [
    { title: 'Quiz: Fotossíntese', subject: 'Biologia', difficulty: 'Médio', diffClass: 'medium', xp: 150 },
    { title: 'Desafio: Funções Trigonométricas', subject: 'Matemática', difficulty: 'Difícil', diffClass: 'hard', xp: 250 },
    { title: 'Exercícios: Present Perfect', subject: 'Inglês', difficulty: 'Fácil', diffClass: 'easy', xp: 100 }
  ];

}