import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';

describe('AuthService.logout', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('avisa o servidor mesmo sem access token em memória, para revogar o cookie de refresh', () => {
    // Sessão restaurada do localStorage, mas sem token: o cenário do reload com refresh
    // que falhou, ou da aba parada além dos 15 minutos.
    expect(auth.token).toBeNull();

    auth.logout();

    const req = http.expectOne((r) => r.url.endsWith('/auth/logout'));
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('limpa a sessão local mesmo que o servidor não responda', () => {
    auth.usuarioLogado.set({ id: 1, nome: 'Ana', cargo: 'Aluno', foto: '', role: 'aluno' });
    expect(auth.usuarioLogado()).not.toBeNull();

    auth.logout();

    expect(auth.usuarioLogado()).toBeNull();
    expect(localStorage.getItem('usuario_nexo')).toBeNull();
    http
      .expectOne((r) => r.url.endsWith('/auth/logout'))
      .error(new ProgressEvent('error'), { status: 0 });
  });
});
