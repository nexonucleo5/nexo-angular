import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Materias } from '../materias/materias';
import { MatriculasDiretor } from '../matriculas-diretor/matriculas-diretor';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-matriculas-wrapper',
  standalone: true,
  imports: [CommonModule, Materias, MatriculasDiretor],
  templateUrl: './matriculas-wrapper.html',
  styleUrl: './matriculas-wrapper.scss'
})
export class MatriculasWrapper{
  public authService = inject(AuthService);
}
