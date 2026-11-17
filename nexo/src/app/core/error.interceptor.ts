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
        retryAfterSegundos: lerRetryAfter(erro),
      };
      return throwError(() => normalizado);
    }),
  );

/**
 * O 429 traz {@code Retry-After} em segundos (o backend nunca usa a forma de data). Valor
 * ausente, não numérico ou não positivo é ignorado: a tela cai na mensagem genérica.
 */
function lerRetryAfter(erro: HttpErrorResponse): number | undefined {
  if (erro.status !== 429) return undefined;
  const segundos = Number(erro.headers?.get('Retry-After'));
  return Number.isFinite(segundos) && segundos > 0 ? Math.ceil(segundos) : undefined;
}
