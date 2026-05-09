import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-dashboards',
  standalone: true,
  imports: [CommonModule], 
  templateUrl: './dashboards.html',
  styleUrl: './dashboards.scss',
})
export class Dashboards {

  atividades = [
    { 
      titulo: 'Exercícios de Redes', 
      materia: 'Networking', 
      xp: 150, 
      tempo: 'há 2 horas', 
      icone: '🌐', 
      progresso: 100, 
      corProgresso: 'blue-fill' 
    },
    { 
      titulo: 'Revisão de Biologia', 
      materia: 'Biologia', 
      xp: 80, 
      tempo: 'há 5 horas', 
      icone: '🧬', 
      progresso: 45, 
      corProgresso: 'green-fill' 
    },
    { 
      titulo: 'Lista de Java', 
      materia: 'Programação', 
      xp: 200, 
      tempo: 'Ontem', 
      icone: '☕', 
      progresso: 70, 
      corProgresso: 'purple-fill' 
    }
  ];

  ranking = [
    { pos: 1, nome: 'Henrique Silva', xp: 4520, foto: 'assets/imagensProjeto/henrique.png', isMe: false },
    { pos: 2, nome: 'Ana Costa', xp: 3980, foto: 'assets/imagensProjeto/ana.png', isMe: false },
    { pos: 3, nome: 'Gabriel Silva', xp: 3450, foto: 'assets/imagensProjeto/gabrielZapelini.png', isMe: true },
    { pos: 4, nome: 'Carla Souza', xp: 2100, foto: 'assets/imagensProjeto/carla.png', isMe: false }
  ];
}