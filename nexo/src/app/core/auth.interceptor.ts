import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

// A recuperação de senha é usada por quem não consegue entrar: não há token para anexar, e
// um 401 daqui não pode disparar a tentativa de renovação lá embaixo.
const ROTAS_PUBLICAS = ['/auth/login', '/auth/refresh', '/auth/senha/'];

/**
 * Anexa o Bearer token a toda chamada da API e, em caso de 401,
 * tenta uma única renovação via refresh token antes de derrubar a sessão.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (ROTAS_PUBLICAS.some((rota) => req.url.includes(rota))) {
    return next(req);
  }

  const requisicao = comToken(req, auth.token);

  return next(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      // O refresh token está num cookie HttpOnly — o cliente não tem como conferir se
      // existe. O que dá para saber é se havia uma sessão: se havia, vale tentar
      // renovar e deixar o servidor decidir pelo cookie.
      const podeRenovar = erro.status === 401 && !!auth.usuarioLogado();
      if (!podeRenovar) {
        return throwError(() => erro);
      }
      return auth.refresh().pipe(
        switchMap((tokens) => next(comToken(req, tokens.token))),
        catchError((erroRefresh) => {
          auth.logout();
          router.navigate(['/login']);
          return throwError(() => erroRefresh);
        }),
      );
    }),
  );
};

function comToken(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
}
