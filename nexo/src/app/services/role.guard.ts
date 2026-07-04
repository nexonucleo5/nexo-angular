import { inject }               from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService }           from '../services/auth.service';

export const roleGuard: CanActivateFn = (route) => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  const usuario = auth.usuarioLogado();
  if (!usuario) return router.createUrlTree(['/login']);

  const rolesPermitidos: string[] = route.data?.['roles'] ?? [];

  // Se a rota não definiu roles, qualquer usuário autenticado pode acessar
  if (!rolesPermitidos.length || rolesPermitidos.includes(usuario.role)) {
    return true;
  }

  // Redireciona para o dashboard do perfil correto
  const dashboards: Record<string, string> = {
    aluno:     '/dashboards',
    diretor:   '/diretor-dashboards',
    professor: '/professor-dashboard',
  };

  return router.createUrlTree([dashboards[usuario.role] ?? '/login']);
};