import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';

export interface Conquista {
  emoji: string;
  titulo: string;
  data: string;
}

export interface MateriaProgresso {
  nome: string;
  porcentagem: number;
  classeCor: string;
}

export interface Estatistica {
  valor: string;
  label: string;
  classeCor: string;
}

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
})
export class Perfil {
  private authService = inject(AuthService);

  /** Expõe o Signal de usuário logado para o template */
  public usuarioLogado = this.authService.usuarioLogado;

  /** Gera e-mail institucional dinamicamente a partir do nome */
  gerarEmail(nome: string): string {
    const partes   = nome.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').split(' ');
    const primeiro = partes[0];
    const ultimo   = partes[partes.length - 1];
    return `${primeiro}.${ultimo}@nexo.escola.com`;
  }

  // ── Dados do aluno ─────────────────────────────────────────────────
  alunoConfig = {
    escola:          'Colégio Modelo',
    progressoGeral:  68,
    metaProgresso:   'concluir 80% até julho',
    conquistas:      { atual: 6, total: 12 },
    sequenciaDias:   7,
    recordeSequencia: 7,
  };

  conquistasRecentes: Conquista[] = [
    { emoji: '🎯', titulo: 'Primeiro Desafio', data: '15 Mar 2026' },
    { emoji: '🔥', titulo: 'Foco Total',        data: '28 Mar 2026' },
    { emoji: '🧬', titulo: 'Biologista',        data: '20 Mar 2026' },
    { emoji: '🏆', titulo: 'Top 3',             data: '01 Abr 2026' },
    { emoji: '⚡', titulo: '1000 XP',           data: '10 Mar 2026' },
    { emoji: '✨', titulo: 'Perfeito',           data: '25 Mar 2026' },
  ];

  materiasProgresso: MateriaProgresso[] = [
    { nome: 'Biologia',   porcentagem: 75, classeCor: 'green-fill'  },
    { nome: 'Matemática', porcentagem: 60, classeCor: 'blue-fill'   },
    { nome: 'História',   porcentagem: 85, classeCor: 'orange-fill' },
    { nome: 'Inglês',     porcentagem: 70, classeCor: 'pink-fill'   },
  ];

  estatisticas: Estatistica[] = [
    { valor: '3.450', label: 'XP Total',               classeCor: 'purple-text' },
    { valor: '42',    label: 'Desafios Concluídos',     classeCor: 'green-text'  },
    { valor: '156h',  label: 'Tempo Total de Estudo',   classeCor: 'blue-text'   },
    { valor: '#3',    label: 'Ranking da Turma',        classeCor: 'gold-text'   },
  ];
}