import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiErro } from './api.models';

/**
 * Normaliza o envelope de erro padrão do backend
 * ({ timestamp, status, error, message, fields }) para consumo uniforme na UI.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(
    catchError((erro: HttpErrorResponse) => {
      const corpo = erro.error as Partial<ApiErro> | null;
      const normalizado: ApiErro = {
        timestamp: corpo?.timestamp ?? new Date().toISOString(),
        status: erro.status,
        error: corpo?.error ?? 'ERRO_DESCONHECIDO',
        message:
          corpo?.message ??
          (erro.status === 0
            ? 'Não foi possível conectar ao servidor. Verifique se o backend está no ar.'
            : 'Erro inesperado ao comunicar com o servidor.'),
        fields: corpo?.fields,
      };
      return throwError(() => normalizado);
    }),
  );
