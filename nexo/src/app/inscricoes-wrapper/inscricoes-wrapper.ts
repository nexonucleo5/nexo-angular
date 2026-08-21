import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Materias } from '../materias/materias';
import { Inscricoes } from '../inscricoes/inscricoes';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-inscricoes-wrapper',
  standalone: true,
  imports: [CommonModule, Materias, Inscricoes],
  templateUrl: './inscricoes-wrapper.html',
  styleUrl: './inscricoes-wrapper.scss'
})
export class InscricoesWrapper {
  public authService = inject(AuthService);
}
