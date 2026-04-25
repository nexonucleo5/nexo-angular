import { Component } from '@angular/core';
import { CommonModule } from '@angular/common'; 
import { FormsModule } from '@angular/forms';

interface Subject {
  title: string;
  level: string;
  progress: number;
  completedLessons: number;
  totalLessons: number;
  iconClass: string; 
  gradientClass: string; 
  buttonClass: string;
  iconText?: string;
}

@Component({
  selector: 'app-materias',
  imports: [CommonModule, FormsModule],
  standalone: true,
  templateUrl: './materias.html',
  styleUrls: ['./materias.scss']
})
export class Materias {
  buscaDeTermos: string = '';
  selecionarNivel: string = 'Todos os Níveis';

  // Lista completa de matérias
  subject: Subject[] = [
    { title: 'Biologia', level: 'Fundamental', progress: 45, completedLessons: 11, totalLessons: 24, iconClass: 'bi-dna', gradientClass: 'green-gradient', buttonClass: 'green-button' },
    { title: 'Matemática', level: 'Médio', progress: 60, completedLessons: 22, totalLessons: 36, iconClass: 'bi-123', gradientClass: 'blue-gradient', buttonClass: 'blue-button' },
    { title: 'Inglês', level: 'Médio', progress: 70, completedLessons: 21, totalLessons: 30, iconClass: '', iconText: 'US', gradientClass: 'pink-gradient', buttonClass: 'pink-button' },
    // Adicione as outras materias depois 
  ];

  // Getter que filtra a lista em tempo real
  get filtroDeBusca() {
    return this.subject.filter(subject => {
      const matchesSearch = subject.title.toLowerCase().includes(this.buscaDeTermos.toLowerCase());
      const matchesLevel = this.selecionarNivel === 'Todos os Níveis' || subject.level === this.selecionarNivel;
      return matchesSearch && matchesLevel;
    });
  }
}