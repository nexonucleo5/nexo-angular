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
  dropdownAberto: boolean = false;

  stats = [
    { label: 'Matérias Ativas', value: '10' },
    { label: 'Aulas Concluídas', value: '162' },
    { label: 'Progresso Médio', value: '58%' },
    { label: 'Tempo Total', value: '134h' }
  ];

  toggleDropdown() {
    this.dropdownAberto = !this.dropdownAberto;
  }

  selecionar(valor: string) {
    this.selecionarNivel = valor;
    this.dropdownAberto = false;
  }

  subjects: Subject[] = [
    {
      title: 'Biologia',
      level: 'Fundamental',
      progress: 45,
      completedLessons: 11,
      totalLessons: 24,
      iconClass: 'bi-dna',
      gradientClass: 'bg-primary',
      buttonClass: 'btn-primary'
    },
    {
      title: 'Matemática',
      level: 'Médio',
      progress: 60,
      completedLessons: 22,
      totalLessons: 36,
      iconClass: 'bi-123',
      gradientClass: 'bg-info',
      buttonClass: 'btn-info'
    },
    {
      title: 'Inglês',
      level: 'Médio',
      progress: 70,
      completedLessons: 21,
      totalLessons: 30,
      iconClass: '',
      iconText: 'US',
      gradientClass: 'bg-warning',
      buttonClass: 'btn-warning'
    }
  ];

  get filtroDeBusca() {
    return this.subjects.filter(subject => {
      const matchesSearch = subject.title.toLowerCase().includes(this.buscaDeTermos.toLowerCase());
      const matchesLevel = this.selecionarNivel === 'Todos os Níveis' || subject.level === this.selecionarNivel;
      return matchesSearch && matchesLevel;
    });
  }
}