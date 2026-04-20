import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Cadastro } from './cadastro/cadastro';
import { Dashboards } from './dashboards/dashboards';
import { Login } from './login/login';
import { Materias } from './materias/materias';
import { Menu } from './menu/menu';

@Component({
  selector: 'app-root',
  imports: [CommonModule, 
    RouterOutlet, 
    Dashboards, 
    Materias, 
    Menu, 
    Cadastro, 
    Login],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  
}
