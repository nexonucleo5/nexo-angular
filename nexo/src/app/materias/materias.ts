import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, SUBJECTS } from './materias.data';

@Component({
  selector: 'app-materias',
  imports: [CommonModule, FormsModule, RouterLink],
  standalone: true,
  templateUrl: './materias.html',
  styleUrls: ['./materias.scss'],
})
export class Materias {
  buscaDeTermos = '';
  selecionarNivel = 'Todos os Níveis';
  dropdownAberto = false;

  subjects: Subject[] = SUBJECTS;

  toggleDropdown() {
    this.dropdownAberto = !this.dropdownAberto;
  }

  selecionar(valor: string) {
    this.selecionarNivel = valor;
    this.dropdownAberto = false;
  }

  get filtroDeBusca(): Subject[] {
    const termo = this.buscaDeTermos.toLowerCase();
    return this.subjects.filter((subject) => {
      const matchesSearch = subject.title.toLowerCase().includes(termo);
      const matchesLevel = this.selecionarNivel === 'Todos os Níveis' || subject.level === this.selecionarNivel;
      return matchesSearch && matchesLevel;
    });
  }

  /** Estatísticas derivadas das disciplinas reais (antes eram números fixos). */
  get stats() {
    const total = this.subjects.length;
    const concluidas = this.subjects.reduce((soma, s) => soma + s.completedLessons, 0);
    const progressoMedio = total
      ? Math.round(this.subjects.reduce((soma, s) => soma + s.progress, 0) / total)
      : 0;
    return [
      { label: 'Matérias Ativas', value: `${total}` },
      { label: 'Aulas Concluídas', value: `${concluidas}` },
      { label: 'Progresso Médio', value: `${progressoMedio}%` },
      { label: 'Tempo Total', value: `${concluidas * 45} min` },
    ];
  }
}
