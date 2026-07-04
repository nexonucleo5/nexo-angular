import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardDiretor } from '../diretor-dashboard/diretor-dashboard';
import { AuthService } from '../services/auth.service';

export interface Atividade {
  titulo: string;
  materia: string;
  xp: number;
  tempo: string;
  icone: string;
  progresso: number;
  corProgresso: string;
}

export interface RankingItem {
  pos: number;
  nome: string;
  xp: number;
  foto: string;
  isMe: boolean;
}

@Component({
  selector: 'app-dashboards',
  standalone: true,
  imports: [CommonModule, DashboardDiretor],
  templateUrl: './dashboards.html',
  styleUrl: './dashboards.scss',
})
export class Dashboards {
  public authService = inject(AuthService);

  // ── Getters dinâmicos (Item 1) ─────────────────────────────────────────────

  /** Saudação baseada na hora do dia */
  get saudacao(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Bom dia';
    if (h < 18) return 'Boa tarde';
    return 'Boa noite';
  }

  /** Primeiro nome do usuário logado */
  get primeiroNome(): string {
    const nome = this.authService.usuarioLogado()?.nome ?? 'Estudante';
    return nome.split(' ')[0];
  }

  /** Cargo/turma do usuário logado */
  get cargoUsuario(): string {
    return this.authService.usuarioLogado()?.cargo ?? '—';
  }

  // ── Dados mockados ─────────────────────────────────────────────────────────

  atividades: Atividade[] = [
    {
      titulo: 'Exercícios de Redes',
      materia: 'Networking',
      xp: 150,
      tempo: 'há 2 horas',
      icone: '🌐',
      progresso: 100,
      corProgresso: 'blue-fill',
    },
    {
      titulo: 'Revisão de Biologia',
      materia: 'Biologia',
      xp: 80,
      tempo: 'há 5 horas',
      icone: '🧬',
      progresso: 45,
      corProgresso: 'green-fill',
    },
    {
      titulo: 'Lista de Java',
      materia: 'Programação',
      xp: 200,
      tempo: 'Ontem',
      icone: '☕',
      progresso: 70,
      corProgresso: 'purple-fill',
    },
  ];

  ranking: RankingItem[] = [
    { pos: 1, nome: 'Henrique Silva', xp: 4520, foto: 'assets/imagensProjeto/henrique.png',         isMe: false },
    { pos: 2, nome: 'Ana Costa',      xp: 3980, foto: 'assets/imagensProjeto/ana.png',              isMe: false },
    { pos: 3, nome: 'Gabriel Silva',  xp: 3450, foto: 'assets/imagensProjeto/gabrielZapelini.png',  isMe: true  },
    { pos: 4, nome: 'Carla Souza',    xp: 2100, foto: 'assets/imagensProjeto/carla.png',            isMe: false },
  ];
}