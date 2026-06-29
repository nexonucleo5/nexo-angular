import { Routes } from '@angular/router';
import { Dashboards } from './dashboards/dashboards';
import { Cadastro } from './cadastro/cadastro';
import { Perfil } from './perfil/perfil';
import { MeusNiveisNotas } from './meus-niveis-notas/meus-niveis-notas';
import { Desafios } from './desafios/desafios';
import { Configuracoes } from './configuracoes/configuracoes';
import { Login } from './login/login';
import { MenuDiretor } from './menu-diretor/menu-diretor';
import { MatriculasWrapper } from './matriculas-wrapper/matriculas-wrapper';
import { Auditoria } from './auditoria/auditoria';
import { RelatoriosDiretor } from './relatorios-diretor/relatorios-diretor';
import { DashboardDiretor } from './diretor-dashboard/diretor-dashboard';
import { GestaoEvasao } from './gestao-evasao/gestao-evasao';
import { MonitoramentoDocente } from './monitoramento-docente/monitoramento-docente';
import { DashboardProfessor } from './dashboard-professor/dashboard-professor';
import { DiarioClasseProfessor } from './diario-classe-professor/diario-classe-professor';
import { Avaliacoes } from './avaliacoes/avaliacoes';
import { Comunicacao } from './comunicacao/comunicacao';

export const routes: Routes = [

  { path: '', redirectTo: '/login', pathMatch: 'full' },

  // ── Compartilhadas ────────────────────────────────────────────────
  { path: 'login',         component: Login         },
  { path: 'perfil',        component: Perfil        },
  { path: 'configuracoes', component: Configuracoes },
  { path: 'dashboards',    component: Dashboards    },

  // ── Aluno ─────────────────────────────────────────────────────────
  { path: 'materias',          component: MatriculasWrapper },
  { path: 'desafios',          component: Desafios          },
  { path: 'meus_niveis_notas', component: MeusNiveisNotas  },
  { path: 'cadastro',          component: Cadastro          },

  // ── Diretor ───────────────────────────────────────────────────────
  { path: 'diretor-dashboards', component: DashboardDiretor    },
  { path: 'matriculas',         component: MatriculasWrapper   },
  { path: 'evasao',             component: GestaoEvasao        },
  { path: 'auditoria',          component: Auditoria           },
  { path: 'relatorios',         component: RelatoriosDiretor   },
  { path: 'monitoramento',      component: MonitoramentoDocente },
  { path: 'menu-diretor',       component: MenuDiretor         },

  // ── Professor ─────────────────────────────────────────────────────
  { path: 'professor-dashboard', component: DashboardProfessor    },
  { path: 'diario',              component: DiarioClasseProfessor },
  { path: 'avaliacao',           component: Avaliacoes            },
  { path: 'comunicacao',         component: Comunicacao           },
  { path: 'notas-engajamento',   component: Dashboards            }, // TODO: componente próprio
];