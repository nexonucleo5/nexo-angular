import { Injectable, signal } from '@angular/core';

export interface Usuario {
  nome: string;
  cargo: string;
  foto: string;
  role: 'aluno' | 'diretor' | 'professor';
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  public usuarioLogado = signal<Usuario | null>(null);

  constructor() {
    const usuarioSalvo = localStorage.getItem('usuario_nexo');
    if (usuarioSalvo) {
      try {
        const usuarioObjeto: Usuario = JSON.parse(usuarioSalvo);
        this.usuarioLogado.set(usuarioObjeto);
      } catch (e) {
        console.error('Erro ao ler usuário do localStorage', e);
        this.logout();
      }
    }
  }

  /** Login agora recebe usuário e senha */
  public login(username: string, password: string): boolean {
    if (!username?.trim() || !password?.trim()) return false;

    const loginLimpo = username.trim().toLowerCase();
    let usuario: Usuario;

    if (loginLimpo === 'diretor' || loginLimpo === 'admin') {
      usuario = {
        nome: 'Diretor Silva',
        cargo: 'Administração Escolar',
        foto: 'assets/imagensProjeto/gabrielZapelini.png',
        role: 'diretor',
      };
    } else if (loginLimpo === 'professor') {
      usuario = {
        nome: 'Prof. Roberto Alves',
        cargo: 'Professor de História',
        foto: 'assets/imagensProjeto/gabrielZapelini.png',
        role: 'professor',
      };
    } else {
      usuario = {
        nome: 'Gabriel Mendes',
        cargo: '2º Ano - Ensino Médio',
        foto: 'assets/imagensProjeto/gabrielZapelini.png',
        role: 'aluno',
      };
    }

    this.atualizarSessao(usuario);
    return true;
  }

  /** Alterna entre Aluno → Diretor → Professor → Aluno */
  public alternarPerfil(): void {
    const usuario = this.usuarioLogado();
    if (!usuario) return;

    const roles: Array<'aluno' | 'diretor' | 'professor'> = ['aluno', 'diretor', 'professor'];
    const indiceAtual = roles.indexOf(usuario.role);
    const proximoRole = roles[(indiceAtual + 1) % roles.length];

    const dadosPorRole: Record<string, Pick<Usuario, 'nome' | 'cargo'>> = {
      aluno:     { nome: 'Gabriel Mendes',      cargo: '2º Ano - Ensino Médio'   },
      diretor:   { nome: 'Diretor Silva',        cargo: 'Administração Escolar'   },
      professor: { nome: 'Prof. Roberto Alves',  cargo: 'Professor de História'   },
    };

    const usuarioAtualizado: Usuario = {
      ...usuario,
      role: proximoRole,
      ...dadosPorRole[proximoRole],
    };

    this.atualizarSessao(usuarioAtualizado);
  }

  public logout(): void {
    localStorage.removeItem('usuario_nexo');
    this.usuarioLogado.set(null);
  }

  private atualizarSessao(usuario: Usuario): void {
    this.usuarioLogado.set(usuario);
    localStorage.setItem('usuario_nexo', JSON.stringify(usuario));
  }
}