import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router'; // <-- IMPORTANTE PARA OS LINKS FUNCIONAREM

@Component({
  selector: 'app-menu-diretor',
  standalone: true,
  imports: [CommonModule, RouterModule], 
  templateUrl: './menu-diretor.html',
  styleUrl: './menu-diretor.scss'
})
export class MenuDiretor {
}