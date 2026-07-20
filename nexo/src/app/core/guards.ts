import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, RoleCliente } from '../services/auth.service';

/** Bloqueia acesso a rotas privadas sem sessão ativa. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.usuarioLogado() ? true : router.createUrlTree(['/login']);
};

/**
 * Restringe a rota às roles informadas (vindas do backend no payload de auth).
 * Última barreira client-side — todo endpoint reforça a checagem no servidor.
 */
export const roleGuard = (...roles: RoleCliente[]): CanActivateFn => {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const usuario = auth.usuarioLogado();
    if (!usuario) return router.createUrlTree(['/login']);
    return roles.includes(usuario.role) ? true : router.createUrlTree(['/dashboards']);
  };
};
