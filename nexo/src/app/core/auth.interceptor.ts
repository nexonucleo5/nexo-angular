import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

const ROTAS_PUBLICAS = ['/auth/login', '/auth/refresh'];

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
      const podeRenovar = erro.status === 401 && !!auth.refreshToken;
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
