import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Cadastro } from './cadastro/cadastro';
import { Dashboards } from './dashboards/dashboards';
import { Login } from './login/login';
import { Materias } from './materias/materias';
import { Menu } from './menu/menu';
import { MenuUsuario } from './menu-usuario/menu-usuario';
import { MenuDiretor } from './menu-diretor/menu-diretor';
import { MenuProfessor } from './menu-professor/menu-professor';
import { AuthService } from './services/auth.service';
import { MatriculasWrapper } from './matriculas-wrapper/matriculas-wrapper';
import { GestaoEvasao } from './gestao-evasao/gestao-evasao';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    Dashboards,
    Materias,
    FormsModule,
    Menu,
    Cadastro,
    Login,
    MenuUsuario,
    MenuDiretor,
    MenuProfessor,
    MatriculasWrapper,
    GestaoEvasao,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  public authService = inject(AuthService);
  temaEscuro: boolean = false;

  ngOnInit() {
    this.aplicarTema();
  }

  alternarPerfil() {
    this.authService.alternarPerfil();
  }

  alternarTema() {
    this.temaEscuro = !this.temaEscuro;
    this.aplicarTema();
  }

  private aplicarTema() {
    const tema = this.temaEscuro ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', tema);
  }
}