import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-desafios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './desafios.html',
  styleUrl: './desafios.scss'
})
export class Desafios {
  buscaDeTermos: string = '';
  materiaSelecionada: string = 'Todas as Matérias';
  nivelSelecionado: string = 'Todos os Níveis';

  stats = [
    { label: 'Desafios Concluídos', value: '42', icon: 'bi-trophy', color: 'orange' },
    { label: 'Taxa de Sucesso', value: '87%', icon: 'bi-bullseye', color: 'green' },
    { label: 'Sequência Atual', value: '7 dias', icon: 'bi-fire', color: 'red' }
  ];

  materias = ['Todas as Matérias', 'Biologia', 'Matemática', 'Inglês', 'História'];
  niveis = ['Todos os Níveis', 'Fácil', 'Médio', 'Difícil'];

  desafios = [
    { titulo: 'Quiz: Fotossíntese', materia: 'Biologia', nivel: 'Médio', xp: 150, tempo: '15 min', status: 'concluido' },
    { titulo: 'Funções Trigonométricas', materia: 'Matemática', nivel: 'Difícil', xp: 250, tempo: '30 min', status: 'progresso', progresso: 40 },
    { titulo: 'Present Perfect', materia: 'Inglês', nivel: 'Fácil', xp: 100, tempo: '20 min', status: 'aberto' }
  ];

  get desafiosFiltrados() {
    return this.desafios.filter(d => 
      (d.titulo.toLowerCase().includes(this.buscaDeTermos.toLowerCase())) &&
      (this.materiaSelecionada === 'Todas as Matérias' || d.materia === this.materiaSelecionada) &&
      (this.nivelSelecionado === 'Todos os Níveis' || d.nivel === this.nivelSelecionado)
    );
  }

  getClasseNivel(nivel: string): string {
    return nivel.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "");
  }
}