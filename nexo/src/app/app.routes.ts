import { Routes } from '@angular/router';
import { authGuard } from './services/auth.guard';
import { roleGuard } from './services/role.guard';

import { Login }             from './login/login';
import { Cadastro }          from './cadastro/cadastro';
import { Perfil }            from './perfil/perfil';
import { Configuracoes }     from './configuracoes/configuracoes';
import { Dashboards }        from './dashboards/dashboards';
import { Desafios }          from './desafios/desafios';
import { MeusNiveisNotas }   from './meus-niveis-notas/meus-niveis-notas';
import { MatriculasWrapper } from './matriculas-wrapper/matriculas-wrapper';

import { MenuDiretor }          from './menu-diretor/menu-diretor';
import { DashboardDiretor }     from './diretor-dashboard/diretor-dashboard';
import { GestaoEvasao }         from './gestao-evasao/gestao-evasao';
import { RelatoriosDiretor }    from './relatorios-diretor/relatorios-diretor';
import { MonitoramentoDocente } from './monitoramento-docente/monitoramento-docente';

import { DashboardProfessor }    from './dashboard-professor/dashboard-professor';
import { DiarioClasseProfessor } from './diario-classe-professor/diario-classe-professor';
import { Avaliacoes }            from './avaliacoes/avaliacoes';
import { NotasEngajamento }      from './notas-engajamento/notas-engajamento';
import { Comunicacao }           from './comunicacao/comunicacao';

export const routes: Routes = [

  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // ── Públicas (sem autenticação) ─────────────────────────────────────────────
  { path: 'login',    component: Login    },
  { path: 'cadastro', component: Cadastro },

  // ── Compartilhadas (qualquer role autenticado) ──────────────────────────────
  {
    path: 'perfil',
    component: Perfil,
    canActivate: [authGuard],
  },
  {
    path: 'configuracoes',
    component: Configuracoes,
    canActivate: [authGuard],
  },
  {
    path: 'dashboards',
    component: Dashboards,
    canActivate: [authGuard],
  },

  // ── Aluno ───────────────────────────────────────────────────────────────────
  {
    path: 'materias',
    component: MatriculasWrapper,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['aluno'] },
  },
  {
    path: 'desafios',
    component: Desafios,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['aluno'] },
  },
  {
    path: 'meus_niveis_notas',
    component: MeusNiveisNotas,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['aluno'] },
  },

  // ── Diretor ─────────────────────────────────────────────────────────────────
  {
    path: 'diretor-dashboards',
    component: DashboardDiretor,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },
  {
    path: 'matriculas',
    component: MatriculasWrapper,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },
  {
    path: 'evasao',
    component: GestaoEvasao,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },
  {
    path: 'relatorios',
    component: RelatoriosDiretor,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },
  {
    path: 'monitoramento',
    component: MonitoramentoDocente,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },
  {
    path: 'menu-diretor',
    component: MenuDiretor,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['diretor'] },
  },

  // ── Professor ────────────────────────────────────────────────────────────────
  {
    path: 'professor-dashboard',
    component: DashboardProfessor,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['professor'] },
  },
  {
    path: 'diario-classe-professor',
    component: DiarioClasseProfessor,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['professor'] },
  },
  {
    path: 'avaliacao',
    component: Avaliacoes,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['professor'] },
  },
  {
    path: 'notas-engajamento',
    component: NotasEngajamento,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['professor'] },
  },
  {
    path: 'comunicacao',
    component: Comunicacao,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['professor'] },
  },

  // Curinga — redireciona URLs inválidas
  { path: '**', redirectTo: '/login' },
];