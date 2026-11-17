import { HttpErrorResponse, HttpHeaders, HttpRequest } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { ApiErro } from './api.models';
import { errorInterceptor } from './error.interceptor';

/** Dispara o interceptor com um erro HTTP e devolve o ApiErro normalizado. */
function normalizar(erro: HttpErrorResponse): Promise<ApiErro> {
  return new Promise((resolve) => {
    (
      errorInterceptor(new HttpRequest('GET', '/api/x'), () => throwError(() => erro)) as Observable<unknown>
    ).subscribe({ error: (e: ApiErro) => resolve(e) });
  });
}

describe('errorInterceptor — Retry-After', () => {
  const corpo429 = { status: 429, error: 'LOGIN_BLOQUEADO', message: 'Muitas tentativas.' };

  it('repassa o tempo de espera do 429 em segundos', async () => {
    const resultado = await normalizar(
      new HttpErrorResponse({
        status: 429,
        error: corpo429,
        headers: new HttpHeaders({ 'Retry-After': '240' }),
      }),
    );

    expect(resultado.status).toBe(429);
    expect(resultado.retryAfterSegundos).toBe(240);
  });

  it('sem o cabeçalho, ou com valor inválido, não inventa espera', async () => {
    const semHeader = await normalizar(new HttpErrorResponse({ status: 429, error: corpo429 }));
    const lixo = await normalizar(
      new HttpErrorResponse({
        status: 429,
        error: corpo429,
        headers: new HttpHeaders({ 'Retry-After': 'amanha' }),
      }),
    );

    expect(semHeader.retryAfterSegundos).toBeUndefined();
    expect(lixo.retryAfterSegundos).toBeUndefined();
  });

  it('só o 429 carrega espera — outros erros ignoram o cabeçalho', async () => {
    const resultado = await normalizar(
      new HttpErrorResponse({
        status: 503,
        error: { message: 'Indisponível' },
        headers: new HttpHeaders({ 'Retry-After': '30' }),
      }),
    );

    expect(resultado.retryAfterSegundos).toBeUndefined();
  });
});
