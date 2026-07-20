import { Component, computed, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SUBJECTS } from '../materias/materias.data';

@Component({
  selector: 'app-disciplina-detalhe',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './disciplina-detalhe.html',
  styleUrl: './disciplina-detalhe.scss',
})
export class DisciplinaDetalhe {
  /** Vem do parâmetro de rota /disciplina/:id (withComponentInputBinding). */
  readonly id = input<string>('');

  readonly disciplina = computed(() => SUBJECTS.find((s) => s.id === this.id()) ?? null);
}
