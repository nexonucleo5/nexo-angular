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
import { DashboardDiretor } from './diretor-dashboard/diretor-dashboard';

// A primeira rota ta redirecionando para o login, para garantir que o usuário sempre comece pela tela de autenticação.
// So alterar o redirectTo para '/dashboards' para iniciar como era antes.
export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'dashboards', component: Dashboards },
  { path: 'materias', component: MatriculasWrapper }, 
  { path: 'matriculas', component: MatriculasWrapper }, 
  { path: 'auditoria', component: Auditoria },
  { path: 'cadastro', component: Cadastro },
  { path: 'perfil', component: Perfil },
  { path: 'desafios', component: Desafios },
  { path: 'meus_niveis_notas', component: MeusNiveisNotas },
  { path: 'configuracoes', component: Configuracoes },
  { path: 'login', component: Login },
  { path: 'menu-diretor', component: MenuDiretor },
  { path: 'diretor-dashboards', component: DashboardDiretor }
];