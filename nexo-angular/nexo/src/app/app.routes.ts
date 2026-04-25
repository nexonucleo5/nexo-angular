import { Routes } from '@angular/router';
import { Dashboards } from './dashboards/dashboards';
import { Materias } from './materias/materias';
import { Cadastro } from './cadastro/cadastro';
import { Perfil } from './perfil/perfil';
import { MeusNiveisNotas } from './meus-niveis-notas/meus-niveis-notas';
import { Desafios } from './desafios/desafios';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboards', pathMatch: 'full' },
  { path: 'dashboards', component: Dashboards },
  { path: 'materias', component: Materias },
  { path: 'cadastro', component: Cadastro },
  { path: 'perfil', component: Perfil },
  { path: 'desafios', component: Desafios },
  { path: 'meus_niveis_notas', component: MeusNiveisNotas },
];
