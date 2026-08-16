import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-menu-secretaria',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './menu-secretaria.html',
  // Mesma barra lateral do diretor — o estilo é o do sistema, não do perfil.
  styleUrl: '../menu-diretor/menu-diretor.scss',
})
export class MenuSecretaria {
}
