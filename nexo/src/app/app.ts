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
export class App implements OnInit {
  // Estado Inicial
  perfilUsuario: 'aluno' | 'diretor' = 'diretor'; 
  temaEscuro: boolean = false;

  ngOnInit() {
    // Define o tema inicial como light ao carregar
    this.aplicarTema();
  }

  // Alterna entre Aluno e Diretor
  alternarPerfil() {
    this.perfilUsuario = this.perfilUsuario === 'aluno' ? 'diretor' : 'aluno';
  }

  // Alterna entre Claro e Escuro
  alternarTema() {
    this.temaEscuro = !this.temaEscuro;
    this.aplicarTema();
  }

  private aplicarTema() {
    const tema = this.temaEscuro ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', tema);
  }
}
