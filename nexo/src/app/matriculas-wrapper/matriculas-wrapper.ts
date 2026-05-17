import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription } from 'rxjs';
import { UserService } from '../services/user';
import { Materias } from '../materias/materias';
import { MatriculasDiretor } from '../matriculas-diretor/matriculas-diretor';

@Component({
  selector: 'app-matriculas-wrapper',
  standalone: true,
  imports: [CommonModule, Materias, MatriculasDiretor],
  templateUrl: './matriculas-wrapper.html',
  styleUrl: './matriculas-wrapper.scss'
})
export class MatriculasWrapper implements OnInit, OnDestroy {
  perfilAtual: string = 'aluno';
  private subPerfis!: Subscription;

  constructor(private userService: UserService) {}

  ngOnInit(): void {
    this.subPerfis = this.userService.perfilUsuario$.subscribe(perfil => {
      this.perfilAtual = perfil;
    });
  }

  ngOnDestroy(): void {
    if (this.subPerfis) {
      this.subPerfis.unsubscribe();
    }
  }
}