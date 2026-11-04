import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, RoleCliente } from '../services/auth.service';

/** Única tela liberada enquanto a senha provisória não for trocada. */
export const ROTA_TROCAR_SENHA = '/trocar-senha';

/**
 * Bloqueia acesso a rotas privadas sem sessão ativa — e, com a senha provisória ainda
 * em uso, a qualquer rota que não seja a troca de senha. Sem isso o desvio feito no
 * login era só uma sugestão: bastava clicar no menu ou digitar outra URL para seguir
 * usando a senha que o administrador viu e repassou.
 */
export const authGuard: CanActivateFn = (_rota, estado) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const usuario = auth.usuarioLogado();
  if (!usuario) return router.createUrlTree(['/login']);
  if (usuario.precisaTrocarSenha && !estado?.url?.startsWith(ROTA_TROCAR_SENHA)) {
    return router.createUrlTree([ROTA_TROCAR_SENHA]);
  }
  return true;
};

/**
 * O contrário do {@link authGuard}: quem já tem sessão não vê a tela de login.
 *
 * <p>Sem isto, a tela de login aparecia DENTRO do sistema — com barra lateral,
 * nome do usuário no topo e o formulário no meio. O caminho era banal: a raiz
 * redireciona para /login, então bastava recarregar a página em "/" com a sessão
 * ativa (ou usar o voltar do navegador) para chegar lá. O layout decide mostrar o
 * shell a partir de {@code usuarioLogado()}, que continuava preenchido — e o
 * resultado era uma tela que não existe: logado e deslogado ao mesmo tempo.
 */
export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const usuario = auth.usuarioLogado();
  if (!usuario) return true;
  return router.createUrlTree([usuario.precisaTrocarSenha ? ROTA_TROCAR_SENHA : '/dashboards']);
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
