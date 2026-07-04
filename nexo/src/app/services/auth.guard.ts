import { inject }               from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService }           from '../services/auth.service';

/**
 * Guard de Autenticação — bloqueia rotas protegidas para usuários não logados.
 *
 * Uso no app.routes.ts:
 *   { path: 'perfil', component: Perfil, canActivate: [authGuard] }
 */
export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  return auth.usuarioLogado()
    ? true
    : router.createUrlTree(['/login']);
};