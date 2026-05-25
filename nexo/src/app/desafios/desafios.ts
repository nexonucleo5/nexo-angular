import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-desafios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './desafios.html',
  styleUrls: ['./desafios.scss'],
})
export class Desafios {
  buscaDeTermos: string = '';
  materiaSelecionada: string = 'Todas as Matérias';
  nivelSelecionado: string = 'Todos os Níveis';

  stats = [
    { label: 'Desafios Concluídos', value: '42', icon: 'bi-trophy', colorClass: 'orange' },
    { label: 'Taxa de Sucesso', value: '87%', icon: 'bi-bullseye', colorClass: 'green' },
    { label: 'Sequência Atual', value: '7 dias', icon: 'bi-fire', colorClass: 'red' }
  ];

  materias: string[] = ['Todas as Matérias', 'Biologia', 'Matemática', 'Inglês', 'História'];
  niveis: string[] = ['Todos os Níveis', 'Fácil', 'Médio', 'Difícil'];

  desafios = [
    { titulo: 'Quiz: Fotossíntese', materia: 'Biologia', nivel: 'Médio', xp: 150, tempo: '15 min', questoes: 10, status: 'concluido' },
    { titulo: 'Desafio: Funções Trigonométricas', materia: 'Matemática', nivel: 'Difícil', xp: 250, tempo: '30 min', questoes: 15, status: 'progresso', progresso: 40 },
    { titulo: 'Exercícios: Present Perfect', materia: 'Inglês', nivel: 'Fácil', xp: 100, tempo: '20 min', questoes: 12, status: 'aberto' },
    { titulo: 'Quiz: Segunda Guerra Mundial', materia: 'História', nivel: 'Médio', xp: 180, tempo: '18 min', questoes: 10, status: 'concluido' }
  ];

  get desafiosFiltrados() {
    return this.desafios.filter(desafio => {
      const termo = this.buscaDeTermos.toLowerCase();
      const condicaoBusca = desafio.titulo.toLowerCase().includes(termo);
      const condicaoMateria = this.materiaSelecionada === 'Todas as Matérias' || desafio.materia === this.materiaSelecionada;
      const condicaoNivel = this.nivelSelecionado === 'Todos os Níveis' || desafio.nivel === this.nivelSelecionado;
      return condicaoBusca && condicaoMateria && condicaoNivel;
    });
  }

  getClasseNivel(nivel: string): string {
    const normalizado = nivel.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "");
    return normalizado === 'facil' ? 'facil' : normalizado === 'medio' ? 'medio' : 'dificil';
  }
}