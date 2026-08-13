import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService, TokenResponse } from '../services/auth.service';

const API = 'http://localhost:8080/api';

function erro401() {
  return new HttpErrorResponse({ status: 401, url: `${API}/alunos` });
}

function tokens(token: string): TokenResponse {
  return {
    // Sem refreshToken: ele saiu do corpo da resposta quando passou a viajar em cookie
    // HttpOnly, e o TokenResponse deixou de ter o campo. O dublê aqui ficou para trás e
    // o teste parou de compilar — o que passou despercebido porque a falha é de
    // compilação do bundle de teste, e não uma expectativa quebrada.
    token,
    usuario: { id: 1, nome: 'Ana', cargo: 'Aluno', foto: '', role: 'ALUNO' },
  };
}

describe('authInterceptor', () => {
  let auth: {
    token: string | null;
    usuarioLogado: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = {
      token: 'token-velho',
      // O interceptor decide se vale tentar renovar olhando se havia sessão: o refresh
      // token está em cookie HttpOnly e o cliente não consegue conferir se existe. O
      // dublê ainda expunha um `refreshToken` legível, que é justamente o que deixou de
      // existir — daí os testes de renovação quebrarem com "não é uma função".
      usuarioLogado: vi.fn(() => ({ id: 1, nome: 'Ana', role: 'ALUNO' })),
      refresh: vi.fn(() => of(tokens('token-novo'))),
      logout: vi.fn(),
    };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  function interceptar(req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
    return TestBed.runInInjectionContext(() => authInterceptor(req, next));
  }

  /** Executa o interceptor e resolve com o que saiu: resposta ou erro. */
  function executar(req: HttpRequest<unknown>, next: HttpHandlerFn) {
    return new Promise<{ resposta?: HttpEvent<unknown>; erro?: unknown }>((resolve) => {
      interceptar(req, next).subscribe({
        next: (resposta) => resolve({ resposta }),
        error: (erro) => resolve({ erro }),
      });
    });
  }

  const get = (url = `${API}/alunos`) => new HttpRequest('GET', url);

  it('anexa o Bearer token na chamada', async () => {
    const enviadas: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (req) => {
      enviadas.push(req);
      return of(new HttpResponse({ status: 200 }));
    };

    await executar(get(), next);

    expect(enviadas[0].headers.get('Authorization')).toBe('Bearer token-velho');
  });

  it('não mexe na requisição quando não há token', async () => {
    auth.token = null;
    const enviadas: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (req) => {
      enviadas.push(req);
      return of(new HttpResponse({ status: 200 }));
    };

    await executar(get(), next);

    expect(enviadas[0].headers.has('Authorization')).toBe(false);
  });

  it('não anexa token nas rotas públicas de login e refresh', async () => {
    const enviadas: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (req) => {
      enviadas.push(req);
      return of(new HttpResponse({ status: 200 }));
    };

    await executar(new HttpRequest('POST', `${API}/auth/login`, {}), next);
    await executar(new HttpRequest('POST', `${API}/auth/refresh`, {}), next);

    expect(enviadas[0].headers.has('Authorization')).toBe(false);
    expect(enviadas[1].headers.has('Authorization')).toBe(false);
  });

  it('renova o token uma vez no 401 e repete a chamada com o token novo', async () => {
    const enviadas: HttpRequest<unknown>[] = [];
    const next: HttpHandlerFn = (req) => {
      enviadas.push(req);
      return enviadas.length === 1
        ? throwError(() => erro401())
        : of(new HttpResponse({ status: 200, body: { ok: true } }));
    };

    const { resposta, erro } = await executar(get(), next);

    expect(erro).toBeUndefined();
    expect((resposta as HttpResponse<unknown>).body).toEqual({ ok: true });
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(enviadas[1].headers.get('Authorization')).toBe('Bearer token-novo');
    expect(auth.logout).not.toHaveBeenCalled();
  });

  it('tenta o refresh UMA vez só — se a chamada repetida também der 401, não renova de novo', async () => {
    // Este é o teste que impede o loop de refresh: sem ele, um backend
    // devolvendo 401 sem parar faria o interceptor renovar indefinidamente.
    const next: HttpHandlerFn = () => throwError(() => erro401());

    const { erro } = await executar(get(), next);

    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(erro).toBeInstanceOf(HttpErrorResponse);
    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('falhando o refresh, faz logout e manda para o login', async () => {
    auth.refresh = vi.fn(() => throwError(() => new HttpErrorResponse({ status: 401 })));
    const next: HttpHandlerFn = () => throwError(() => erro401());

    const { erro } = await executar(get(), next);

    expect(auth.logout).toHaveBeenCalledTimes(1);
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
    expect(erro).toBeInstanceOf(HttpErrorResponse);
  });

  // O gatilho deixou de ser "tem refresh token guardado?" — ele está em cookie HttpOnly e
  // o cliente não o enxerga. Passou a ser "havia sessão?": sem usuário, não há o que
  // renovar, e insistir só geraria uma chamada a /refresh fadada ao 401.
  it('sem sessão, nem tenta renovar', async () => {
    auth.usuarioLogado = vi.fn(() => null);
    const next: HttpHandlerFn = () => throwError(() => erro401());

    const { erro } = await executar(get(), next);

    expect(auth.refresh).not.toHaveBeenCalled();
    expect(auth.logout).not.toHaveBeenCalled();
    expect(erro).toBeInstanceOf(HttpErrorResponse);
  });

  it('erro que não é 401 passa direto, sem renovar nem deslogar', async () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 500 }));

    const { erro } = await executar(get(), next);

    expect(auth.refresh).not.toHaveBeenCalled();
    expect(auth.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect((erro as HttpErrorResponse).status).toBe(500);
  });

  it('403 não dispara refresh — a sessão é válida, falta permissão', async () => {
    const next: HttpHandlerFn = () => throwError(() => new HttpErrorResponse({ status: 403 }));

    const { erro } = await executar(get(), next);

    expect(auth.refresh).not.toHaveBeenCalled();
    expect((erro as HttpErrorResponse).status).toBe(403);
  });
});
