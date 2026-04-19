import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Dashboards } from './dashboards/dashboards';
import { Materias } from './materias/materias';
import { Menu } from './menu/menu';
import { MeusNiveisNotas } from './meus-niveis-notas/meus-niveis-notas';
import { Perfil } from './perfil/perfil';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Dashboards, Materias, Menu, MeusNiveisNotas, Perfil],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  
}
