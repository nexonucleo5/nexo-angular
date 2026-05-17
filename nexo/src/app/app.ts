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

// 1. IMPORTAR o Serviço e o seu novo Componente Wrapper
import { UserService } from './services/user';
import { MatriculasWrapper } from './matriculas-wrapper/matriculas-wrapper';

@Component({
  selector: 'app-root',
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
  // Estado Inicial (Mantive começando como 'diretor' igual ao seu código)
  perfilUsuario: 'aluno' | 'diretor' = 'diretor'; 
  temaEscuro: boolean = false;

  // 3. INJETAR o UserService no construtor da classe
  constructor(private userService: UserService) {}

  ngOnInit() {
    // Define o tema inicial como light ao carregar
    this.aplicarTema();

    // Sincroniza o estado inicial do serviço com o seu perfil inicial ('diretor')
    this.userService.alternarPerfil(this.perfilUsuario);
  }

  // Alterna entre Aluno e Diretor
  alternarPerfil() {
    this.perfilUsuario = this.perfilUsuario === 'aluno' ? 'diretor' : 'aluno';
    
    // 4. AVISAR o serviço global sobre a mudança de perfil
    this.userService.alternarPerfil(this.perfilUsuario);
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