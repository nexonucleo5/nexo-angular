import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface Desafio {
  titulo: string;
  materia: string;
  nivel: 'Fácil' | 'Médio' | 'Difícil';
  xp: number;
  tempo: string;
  status: 'concluido' | 'progresso' | 'aberto';
  progresso?: number;
}

export interface StatDesafio {
  label: string;
  value: string;
  icon: string;
  color: string;
}

@Component({
  selector: 'app-desafios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './desafios.html',
  styleUrl: './desafios.scss',
})
export class Desafios {
  buscaDeTermos     = '';
  materiaSelecionada = 'Todas as Matérias';
  nivelSelecionado   = 'Todos os Níveis';

  stats: StatDesafio[] = [
    { label: 'Desafios Concluídos', value: '42',    icon: 'bi-trophy',  color: 'orange' },
    { label: 'Taxa de Sucesso',     value: '87%',   icon: 'bi-bullseye', color: 'green'  },
    { label: 'Sequência Atual',     value: '7 dias', icon: 'bi-fire',    color: 'red'    },
  ];

  materias = ['Todas as Matérias', 'Biologia', 'Matemática', 'Inglês', 'História'];
  niveis   = ['Todos os Níveis',   'Fácil',    'Médio',      'Difícil'];

  desafios: Desafio[] = [
    { titulo: 'Quiz: Fotossíntese',         materia: 'Biologia',   nivel: 'Médio',  xp: 150, tempo: '15 min', status: 'concluido'               },
    { titulo: 'Funções Trigonométricas',    materia: 'Matemática', nivel: 'Difícil', xp: 250, tempo: '30 min', status: 'progresso', progresso: 40 },
    { titulo: 'Present Perfect',            materia: 'Inglês',     nivel: 'Fácil',  xp: 100, tempo: '20 min', status: 'aberto'                  },
    { titulo: 'Revolução Industrial',       materia: 'História',   nivel: 'Médio',  xp: 180, tempo: '25 min', status: 'aberto'                  },
    { titulo: 'Ecossistemas e Biomas',      materia: 'Biologia',   nivel: 'Fácil',  xp: 120, tempo: '15 min', status: 'concluido'               },
    { titulo: 'Derivadas e Integrais',      materia: 'Matemática', nivel: 'Difícil', xp: 300, tempo: '40 min', status: 'progresso', progresso: 20 },
  ];

  get desafiosFiltrados(): Desafio[] {
    return this.desafios.filter(d =>
      d.titulo.toLowerCase().includes(this.buscaDeTermos.toLowerCase()) &&
      (this.materiaSelecionada === 'Todas as Matérias' || d.materia === this.materiaSelecionada) &&
      (this.nivelSelecionado   === 'Todos os Níveis'   || d.nivel   === this.nivelSelecionado)
    );
  }

  /** Retorna a classe CSS correspondente ao nível do desafio */
  getClasseNivel(nivel: string): string {
    const mapa: Record<string, string> = {
      'Fácil':  'facil',
      'Médio':  'medio',
      'Difícil': 'dificil',
    };
    return mapa[nivel] ?? 'medio';
  }
}