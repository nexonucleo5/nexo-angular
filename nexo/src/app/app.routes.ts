import { Routes } from '@angular/router';

// ── Componentes compartilhados / Aluno ────────────────────────────────────────
import { Login }            from './login/login';
import { Perfil }           from './perfil/perfil';
import { Configuracoes }    from './configuracoes/configuracoes';
import { Dashboards }       from './dashboards/dashboards';
import { Cadastro }         from './cadastro/cadastro';
import { Desafios }         from './desafios/desafios';
import { MeusNiveisNotas }  from './meus-niveis-notas/meus-niveis-notas';
import { MatriculasWrapper }from './matriculas-wrapper/matriculas-wrapper';

// ── Componentes do Diretor ────────────────────────────────────────────────────
import { MenuDiretor }         from './menu-diretor/menu-diretor';
import { DashboardDiretor }    from './diretor-dashboard/diretor-dashboard';
import { GestaoEvasao }        from './gestao-evasao/gestao-evasao';
import { Auditoria }           from './auditoria/auditoria';
import { RelatoriosDiretor }   from './relatorios-diretor/relatorios-diretor';
import { MonitoramentoDocente }from './monitoramento-docente/monitoramento-docente';
import { MatriculasDiretor }   from './matriculas-diretor/matriculas-diretor';

// ── Componentes do Professor ──────────────────────────────────────────────────
import { DashboardProfessor }    from './dashboard-professor/dashboard-professor';
import { DiarioClasseProfessor } from './diario-classe-professor/diario-classe-professor';
import { Avaliacoes }            from './avaliacoes/avaliacoes';
import { NotasEngajamento }      from './notas-engajamento/notas-engajamento';
import { Comunicacao }           from './comunicacao/comunicacao';

export const routes: Routes = [

  // Redireciona raiz para login
  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // ── Públicas ────────────────────────────────────────────────────────────────
  { path: 'login', component: Login },

  // ── Compartilhadas (todos os roles) ─────────────────────────────────────────
  { path: 'perfil',        component: Perfil        },
  { path: 'configuracoes', component: Configuracoes },
  { path: 'dashboards',    component: Dashboards    },

  // ── Aluno ────────────────────────────────────────────────────────────────────
  { path: 'materias',          component: MatriculasWrapper },
  { path: 'desafios',          component: Desafios          },
  { path: 'meus_niveis_notas', component: MeusNiveisNotas   },
  { path: 'cadastro',          component: Cadastro          },

  // ── Diretor ──────────────────────────────────────────────────────────────────
  { path: 'diretor-dashboards', component: DashboardDiretor    },
  { path: 'matriculas',         component: MatriculasWrapper   },  // wrapper redireciona por role
  { path: 'evasao',             component: GestaoEvasao        },
  { path: 'auditoria',          component: Auditoria           },
  { path: 'relatorios',         component: RelatoriosDiretor   },
  { path: 'monitoramento',      component: MonitoramentoDocente },
  { path: 'menu-diretor',       component: MenuDiretor         },

  // ── Professor ─────────────────────────────────────────────────────────────────
  // ATENÇÃO: paths exatamente iguais aos routerLink do menu-professor.html
  { path: 'professor-dashboard',   component: DashboardProfessor    },
  { path: 'diario-classe-professor', component: DiarioClasseProfessor }, // ← era 'diario' (causava erro)
  { path: 'avaliacao',             component: Avaliacoes            },
  { path: 'notas-engajamento',     component: NotasEngajamento      },
  { path: 'comunicacao',           component: Comunicacao           },
  { path: '**', redirectTo: '/login' },
];