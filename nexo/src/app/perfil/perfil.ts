import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
})
export class Perfil {
  private authService = inject(AuthService);
  
  // Expõe o Signal para o HTML do perfil
  public usuarioLogado = this.authService.usuarioLogado;

  // Dados adicionais mockados que não vêm do login básico
  alunoConfig = {
    escola: 'Colégio Modelo',
    progressoGeral: 68,
    metaProgresso: 'concluir 80% até julho',
    conquistas: { atual: 6, total: 12 },
    sequenciaDias: 7,
    recordeSequencia: 7
  };

  conquistasRecentes = [
    { emoji: '🎯', titulo: 'Primeiro Desafio', data: '15 Mar 2026' },
    { emoji: '🔥', titulo: 'Foco Total', data: '28 Mar 2026' },
    { emoji: '🧬', titulo: 'Biologista', data: '20 Mar 2026' },
    { emoji: '🏆', titulo: 'Top 3', data: '01 Abr 2026' },
    { emoji: '⚡', titulo: '1000 XP', data: '10 Mar 2026' },
    { emoji: '✨', titulo: 'Perfeito', data: '25 Mar 2026' }
  ];

  materiasProgresso = [
    { nome: 'Biologia', porcentagem: 75, classeCor: 'green-fill' },
    { nome: 'Matemática', porcentagem: 60, classeCor: 'blue-fill' },
    { nome: 'História', porcentagem: 85, classeCor: 'orange-fill' },
    { nome: 'Inglês', porcentagem: 70, classeCor: 'pink-fill' }
  ];

  estatisticas = [
    { valor: '3.450', label: 'XP Total', classeCor: 'purple-text' },
    { valor: '42', label: 'Desafios Concluídos', classeCor: 'green-text' },
    { valor: '156h', label: 'Tempo Total de Estudo', classeCor: 'blue-text' },
    { valor: '#3', label: 'Ranking da Turma', classeCor: 'gold-text' }
  ];
}