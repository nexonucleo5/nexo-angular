import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Cadastro } from './cadastro/cadastro';
import { Dashboards } from './dashboards/dashboards';
import { Login } from './login/login';
import { Materias } from './materias/materias';
import { Menu } from './menu/menu';
import { MenuUsuario } from './menu-usuario/menu-usuario';
import { MenuDiretor } from './menu-diretor/menu-diretor';
import { AuthService } from './services/auth.service';

// 1. IMPORTAR o Serviço e o seu novo Componente Wrapper
import { UserService } from './services/user';
import { MatriculasWrapper } from './matriculas-wrapper/matriculas-wrapper';

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
    // 2. ADICIONAR o Wrapper na lista de imports do componente Standalone
    MatriculasWrapper
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  temaEscuro: boolean = false;

  constructor(public authService: AuthService) {}

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