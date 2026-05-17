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

export const routes: Routes = [
  { path: '', redirectTo: '/dashboards', pathMatch: 'full' },
  { path: 'dashboards', component: Dashboards },
  { path: 'materias', component: MatriculasWrapper },   
  { path: 'cadastro', component: Cadastro },
  { path: 'perfil', component: Perfil },
  { path: 'desafios', component: Desafios },
  { path: 'meus_niveis_notas', component: MeusNiveisNotas },
  { path: 'configuracoes', component: Configuracoes },
  { path: 'login', component: Login },
  { path: 'menu-diretor', component: MenuDiretor }
];