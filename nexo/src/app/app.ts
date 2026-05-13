import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Cadastro } from './cadastro/cadastro';
import { Dashboards } from './dashboards/dashboards';
import { Login } from './login/login';
import { Materias } from './materias/materias';
import { Menu } from './menu/menu';
import { FormsModule } from '@angular/forms';
import { MenuUsuario } from './menu-usuario/menu-usuario';
import { MenuDiretor } from './menu-diretor/menu-diretor';

@Component({
  selector: 'app-root',
  imports: [CommonModule, 
    RouterOutlet, 
    Dashboards, 
    Materias,
    FormsModule, 
    Menu, 
    Cadastro, 
    Login,
    MenuUsuario,
    MenuDiretor],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {

  perfilUsuario: string = 'diretor';

  alternarPerfil() {
    this.perfilUsuario = this.perfilUsuario === 'aluno' ? 'diretor' : 'aluno';
  }
  
}
