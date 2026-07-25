import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { UsuariosService } from '../api/usuarios.service';
import { AlunoDashboardService } from '../api/aluno-dashboard.service';
import { PerfilProfessor } from '../perfil-professor/perfil-professor';
import { PerfilDiretor } from '../perfil-diretor/perfil-diretor';

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
  imports: [CommonModule, FormsModule, PerfilProfessor, PerfilDiretor],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
})
export class Perfil {
  private authService = inject(AuthService);
  private usuarios = inject(UsuariosService);
  private alunoDashboard = inject(AlunoDashboardService);
  private router = inject(Router);

  public usuarioLogado = this.authService.usuarioLogado;

  // ── Edição de perfil ───────────────────────────────────────────────
  readonly editando = signal(false);
  readonly salvando = signal(false);
  readonly mensagem = signal('');
  formNome = '';
  formFoto = '';

  // ── Estatísticas do aluno (gamificação real quando disponível) ──────
  readonly estatisticas = signal<Estatistica[]>([
    { valor: '—', label: 'XP Total', classeCor: 'purple-text' },
    { valor: '—', label: 'Ofensiva (dias)', classeCor: 'green-text' },
    { valor: '—', label: 'Tarefas Hoje', classeCor: 'blue-text' },
    { valor: '—', label: 'Ranking da Turma', classeCor: 'gold-text' },
  ]);

  readonly progressoGeral = signal(0);

  materiasProgresso: MateriaProgresso[] = [
    { nome: 'Biologia', porcentagem: 75, classeCor: 'green-fill' },
    { nome: 'Matemática', porcentagem: 60, classeCor: 'blue-fill' },
    { nome: 'História', porcentagem: 85, classeCor: 'orange-fill' },
    { nome: 'Inglês', porcentagem: 70, classeCor: 'pink-fill' },
  ];

  alunoConfig = { escola: 'Colégio Nexo' };

  constructor() {
    if (this.usuarioLogado()?.role === 'aluno') {
      this.alunoDashboard.dashboard().subscribe({
        next: (d) => {
          this.estatisticas.set([
            { valor: `${d.xpSemana}`, label: 'XP na Semana', classeCor: 'purple-text' },
            { valor: `${d.ofensivaDias}`, label: 'Ofensiva (dias)', classeCor: 'green-text' },
            { valor: `${d.tarefasFeitasHoje}/${d.tarefasHoje}`, label: 'Tarefas Hoje', classeCor: 'blue-text' },
            { valor: `#${d.posicao}`, label: 'Ranking da Turma', classeCor: 'gold-text' },
          ]);
          const meta = d.metaSemanalXp || 1000;
          this.progressoGeral.set(Math.min(100, Math.round((d.xpSemana / meta) * 100)));
        },
        error: () => {},
      });
    }
  }

  gerarEmail(nome: string): string {
    const partes = nome.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').split(' ');
    return `${partes[0]}.${partes[partes.length - 1]}@nexo.escola.com`;
  }

  abrirEdicao(): void {
    const u = this.usuarioLogado();
    if (!u) return;
    this.formNome = u.nome;
    this.formFoto = u.foto;
    this.mensagem.set('');
    this.editando.set(true);
  }

  cancelarEdicao(): void {
    this.editando.set(false);
  }

  salvarPerfil(): void {
    if (!this.formNome.trim() || this.salvando()) return;
    this.salvando.set(true);
    this.usuarios.atualizarPerfil({ nome: this.formNome.trim(), foto: this.formFoto.trim() }).subscribe({
      next: () => {
        this.salvando.set(false);
        this.editando.set(false);
        this.mensagem.set('✅ Perfil atualizado!');
        setTimeout(() => this.mensagem.set(''), 3000);
      },
      error: () => {
        this.salvando.set(false);
        this.mensagem.set('❌ Falha ao atualizar o perfil.');
      },
    });
  }

  irParaSenha(): void {
    this.router.navigate(['/trocar-senha']);
  }
}
