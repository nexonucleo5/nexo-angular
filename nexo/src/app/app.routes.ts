import { Routes } from '@angular/router';
import { authGuard, roleGuard, visitanteGuard } from './core/guards';

/**
 * Rotas com lazy loading (loadComponent) por perfil + guards usando a role
 * vinda do backend — o controle de acesso deixa de ser apenas visual.
 */
export const routes: Routes = [

  // Redireciona raiz para login
  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // ── Públicas ────────────────────────────────────────────────────────────────
  {
    // visitanteGuard: com sessão ativa, /login manda para o painel em vez de
    // desenhar o formulário dentro do sistema (ver o comentário do guard).
    path: 'login',
    canActivate: [visitanteGuard],
    loadComponent: () => import('./login/login').then((m) => m.Login),
  },

  // ── Compartilhadas (todos os roles) ─────────────────────────────────────────
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () => import('./perfil/perfil').then((m) => m.Perfil),
  },
  {
    path: 'configuracoes',
    canActivate: [authGuard],
    loadComponent: () => import('./configuracoes/configuracoes').then((m) => m.Configuracoes),
  },
  {
    path: 'trocar-senha',
    canActivate: [authGuard],
    loadComponent: () => import('./trocar-senha/trocar-senha').then((m) => m.TrocarSenha),
  },
  {
    path: 'dashboards',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboards/dashboards').then((m) => m.Dashboards),
  },
  {
    // Referência de perfis e permissões — a mesma matriz que o backend aplica.
    path: 'sobre',
    canActivate: [authGuard],
    loadComponent: () => import('./sobre/sobre').then((m) => m.Sobre),
  },
  {
    path: 'suporte',
    canActivate: [authGuard],
    loadComponent: () => import('./suporte/suporte').then((m) => m.Suporte),
  },
  {
    // Chat em tempo real entre papéis diferentes (aluno ↔ professor ↔ diretor)
    path: 'mensagens',
    canActivate: [authGuard, roleGuard('diretor', 'professor', 'aluno')],
    loadComponent: () => import('./chat/chat').then((m) => m.Chat),
  },

  // ── Aluno ────────────────────────────────────────────────────────────────────
  {
    path: 'materias',
    canActivate: [authGuard, roleGuard('aluno')],
    loadComponent: () => import('./inscricoes-wrapper/inscricoes-wrapper').then((m) => m.InscricoesWrapper),
  },
  {
    path: 'disciplina/:id',
    canActivate: [authGuard, roleGuard('aluno')],
    loadComponent: () => import('./disciplina-detalhe/disciplina-detalhe').then((m) => m.DisciplinaDetalhe),
  },
  {
    path: 'desafios',
    canActivate: [authGuard, roleGuard('aluno')],
    loadComponent: () => import('./desafios/desafios').then((m) => m.Desafios),
  },
  {
    path: 'desafios/:id/quiz',
    canActivate: [authGuard, roleGuard('aluno')],
    loadComponent: () => import('./quiz-desafio/quiz-desafio').then((m) => m.QuizDesafio),
  },
  {
    path: 'meus-niveis-notas',
    canActivate: [authGuard, roleGuard('aluno')],
    loadComponent: () => import('./meus-niveis-notas/meus-niveis-notas').then((m) => m.MeusNiveisNotas),
  },
  // Era a única rota em snake_case do projeto. Mantido para não quebrar link
  // já compartilhado; pode sair quando não houver mais acesso pelo path antigo.
  { path: 'meus_niveis_notas', redirectTo: '/meus-niveis-notas', pathMatch: 'full' },

  // ── Administrador ────────────────────────────────────────────────────────────
  // Contas e catálogo. O diretor entra junto, como já entrava no painel da
  // secretaria que estas telas substituíram.
  {
    path: 'admin-dashboard',
    canActivate: [authGuard, roleGuard('admin', 'diretor')],
    loadComponent: () => import('./admin-dashboard/admin-dashboard').then((m) => m.AdminDashboard),
  },
  {
    path: 'contas',
    canActivate: [authGuard, roleGuard('admin', 'diretor')],
    loadComponent: () => import('./contas/contas').then((m) => m.Contas),
  },
  {
    path: 'catalogo',
    canActivate: [authGuard, roleGuard('admin', 'diretor')],
    loadComponent: () => import('./catalogo/catalogo').then((m) => m.Catalogo),
  },
  // Path antigo do painel da secretaria: mantido para não quebrar link já
  // compartilhado ou favorito de quem usava a tela.
  { path: 'secretaria-dashboard', redirectTo: '/admin-dashboard', pathMatch: 'full' },

  // ── Diretor ──────────────────────────────────────────────────────────────────
  {
    // O administrador cria as contas de acesso; o backend espelha em
    // POST /api/alunos com hasAnyRole('DIRETOR','ADMIN').
    path: 'cadastro',
    canActivate: [authGuard, roleGuard('diretor', 'admin')],
    loadComponent: () => import('./cadastro/cadastro').then((m) => m.Cadastro),
  },
  {
    path: 'diretor-dashboards',
    canActivate: [authGuard, roleGuard('diretor')],
    loadComponent: () => import('./diretor-dashboard/diretor-dashboard').then((m) => m.DashboardDiretor),
  },
  {
    // wrapper redireciona por role (aluno vê matérias, gestão vê inscrições)
    path: 'inscricoes',
    canActivate: [authGuard],
    loadComponent: () => import('./inscricoes-wrapper/inscricoes-wrapper').then((m) => m.InscricoesWrapper),
  },
  // Path antigo, pelo mesmo motivo do /secretaria-dashboard acima.
  { path: 'matriculas', redirectTo: '/inscricoes', pathMatch: 'full' },
  {
    path: 'evasao',
    canActivate: [authGuard, roleGuard('diretor')],
    loadComponent: () => import('./gestao-evasao/gestao-evasao').then((m) => m.GestaoEvasao),
  },
  {
    path: 'relatorios',
    canActivate: [authGuard, roleGuard('diretor')],
    loadComponent: () => import('./relatorios-diretor/relatorios-diretor').then((m) => m.RelatoriosDiretor),
  },
  {
    path: 'monitoramento',
    canActivate: [authGuard, roleGuard('diretor')],
    loadComponent: () => import('./monitoramento-docente/monitoramento-docente').then((m) => m.MonitoramentoDocente),
  },
  {
    path: 'menu-diretor',
    canActivate: [authGuard, roleGuard('diretor')],
    loadComponent: () => import('./menu-diretor/menu-diretor').then((m) => m.MenuDiretor),
  },

  // ── Professor ─────────────────────────────────────────────────────────────────
  // ATENÇÃO: paths exatamente iguais aos routerLink do menu-professor.html
  {
    path: 'professor-dashboard',
    canActivate: [authGuard, roleGuard('professor')],
    loadComponent: () => import('./dashboard-professor/dashboard-professor').then((m) => m.DashboardProfessor),
  },
  {
    path: 'diario-classe-professor',
    canActivate: [authGuard, roleGuard('professor')],
    loadComponent: () => import('./diario-classe-professor/diario-classe-professor').then((m) => m.DiarioClasseProfessor),
  },
  {
    path: 'avaliacao',
    canActivate: [authGuard, roleGuard('professor')],
    loadComponent: () => import('./avaliacoes/avaliacoes').then((m) => m.Avaliacoes),
  },
  {
    path: 'notas-engajamento',
    canActivate: [authGuard, roleGuard('professor')],
    loadComponent: () => import('./notas-engajamento/notas-engajamento').then((m) => m.NotasEngajamento),
  },
  {
    path: 'comunicacao',
    canActivate: [authGuard, roleGuard('professor')],
    loadComponent: () => import('./comunicacao/comunicacao').then((m) => m.Comunicacao),
  },

  { path: '**', redirectTo: '/login' },
];
