import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SecretariaService } from '../api/secretaria.service';
import { DashboardSecretariaDTO } from '../core/api.models';

@Component({
  selector: 'app-secretaria-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './secretaria-dashboard.html',
  styleUrl: './secretaria-dashboard.scss',
})
export class SecretariaDashboard {
  private readonly secretaria = inject(SecretariaService);

  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly dados = signal<DashboardSecretariaDTO | null>(null);

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.secretaria.dashboard().subscribe({
      next: (d) => {
        this.dados.set(d);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }
}
