import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-menu-professor',
  imports: [CommonModule, RouterModule],
  templateUrl: './menu-professor.html',
  styleUrl: './menu-professor.scss'
})
export class MenuProfessor {}
