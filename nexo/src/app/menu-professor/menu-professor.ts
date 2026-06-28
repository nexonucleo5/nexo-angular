import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-menu-professor',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './menu-professor.html',
  styleUrl: './menu-professor.scss',
})
export class MenuProfessor {}