import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { authGuard, roleGuard, visitanteGuard } from './guards';
import { AuthService, RoleCliente, Usuario } from '../services/auth.service';

/** Os guards ignoram os dois argumentos; passar stubs vazios basta. */
const ROTA = {} as ActivatedRouteSnapshot;
const ESTADO = {} as RouterStateSnapshot;

function usuarioCom(role: RoleCliente): Usuario {
  return { id: 1, nome: 'Ana', cargo: 'Aluno', foto: '', role };
}

describe('guards de rota', () => {
  let sessao: ReturnType<typeof signal<Usuario | null>>;

  beforeEach(() => {
    sessao = signal<Usuario | null>(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { usuarioLogado: sessao } },
      ],
    });
  });

  function rodar(guard: ReturnType<typeof roleGuard> | typeof authGuard | typeof visitanteGuard) {
    return TestBed.runInInjectionContext(() => guard(ROTA, ESTADO));
  }

  function destinoDe(resultado: unknown): string {
    expect(resultado).toBeInstanceOf(UrlTree);
    return (resultado as UrlTree).toString();
  }

  describe('authGuard', () => {
    it('manda para o login quando não há sessão', () => {
      expect(destinoDe(rodar(authGuard))).toBe('/login');
    });

    it('libera quando há sessão', () => {
      sessao.set(usuarioCom('aluno'));

      expect(rodar(authGuard)).toBe(true);
    });
  });

  describe('roleGuard', () => {
    it('manda para o login quando não há sessão', () => {
      expect(destinoDe(rodar(roleGuard('diretor')))).toBe('/login');
    });

    it('libera quando a role está entre as permitidas', () => {
      sessao.set(usuarioCom('professor'));

      expect(rodar(roleGuard('professor'))).toBe(true);
    });

    it('libera quando a rota aceita várias roles', () => {
      sessao.set(usuarioCom('aluno'));

      expect(rodar(roleGuard('diretor', 'professor', 'aluno'))).toBe(true);
    });

    it('manda para o dashboard quando a role não está entre as permitidas', () => {
      sessao.set(usuarioCom('aluno'));

      expect(destinoDe(rodar(roleGuard('diretor')))).toBe('/dashboards');
    });

    it('não libera nada quando a lista de roles é vazia', () => {
      // O guard morto que existia em services/ liberava qualquer autenticado
      // nesse caso. Este teste fixa o comportamento oposto: sem role na lista,
      // ninguém passa.
      sessao.set(usuarioCom('diretor'));

      expect(destinoDe(rodar(roleGuard()))).toBe('/dashboards');
    });
  });


  describe('visitanteGuard', () => {
    it('libera a tela de login para quem não tem sessão', () => {
      expect(rodar(visitanteGuard)).toBe(true);
    });

    it('desvia para o painel quem já está logado', () => {
      // Trava o bug em que /login era desenhado dentro do sistema: a raiz
      // redireciona para /login, então recarregar em "/" com sessão ativa levava
      // ao formulário de login com barra lateral e nome do usuário na tela.
      sessao.set(usuarioCom('aluno'));

      expect(destinoDe(rodar(visitanteGuard))).toBe('/dashboards');
    });
  });

  it('o guard lê a sessão a cada chamada, não no momento em que é criado', () => {
    const guard = roleGuard('diretor');
    sessao.set(usuarioCom('aluno'));
    expect(destinoDe(rodar(guard))).toBe('/dashboards');

    sessao.set(usuarioCom('diretor'));
    expect(rodar(guard)).toBe(true);
  });

  it('o Router de verdade resolve os destinos usados pelos guards', () => {
    const router = TestBed.inject(Router);

    expect(router.createUrlTree(['/login']).toString()).toBe('/login');
    expect(router.createUrlTree(['/dashboards']).toString()).toBe('/dashboards');
  });
});
