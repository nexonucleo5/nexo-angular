import { Injectable, signal } from '@angular/core';

// Definindo a estrutura de um usuário
export interface Usuario {
  nome: string;
  cargo: string;
  foto: string;
  role: 'aluno' | 'diretor'; // Adicionando um campo opcional para o papel do usuário
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  // O estado começa como 'null' (ninguém logado)
  public usuarioLogado = signal<Usuario | null>(null);

  constructor() {
    const usuarioSalvo = localStorage.getItem('usuario_nexo');
    if (usuarioSalvo) {
      try {
        const usuarioObjeto: Usuario = JSON.parse(usuarioSalvo);
        this.usuarioLogado.set(usuarioObjeto);
      } catch (e) {
        console.error('Erro ao ler usuário do localStorage', e);
        this.logout(); // Se o dado estiver corrompido, limpa a sessão
      }
    }
  }

  /** Método para simular o login no frontend */
  public login(username: string): void {
    const loginLimpo = username.trim().toLowerCase();
    let usuario: Usuario;

    // Simula o Login do Diretor
    if (loginLimpo === 'diretor' || loginLimpo === 'admin') {
      usuario = {
        nome: 'Diretor Silva',
        cargo: 'Administração Escolar',
        foto: 'assets/imagensProjeto/gabrielZapelini.png',
        role: 'diretor',
      };
    } else {
      // Simula o Login do Aluno (qualquer outro username)
      usuario = {
        nome: 'Gabriel Mendes',
        cargo: '2º Ano - Ensino Médio',
        foto: 'assets/imagensProjeto/gabrielZapelini.png',
        role: 'aluno',
      };
    }

    // 3. Salva o objeto modificado no Signal e no LocalStorage
    this.atualizarSessao(usuario);
  }

  /** Método para alternar perfil rapidamente em desenvolvimento */
  public alternarPerfil(): void {
    const usuario = this.usuarioLogado();
    if (!usuario) return;

    const novoRole = usuario.role === 'aluno' ? 'diretor' : 'aluno';

    const usuarioAtualizado: Usuario = {
      ...usuario,
      role: novoRole,
      nome: novoRole === 'diretor' ? 'Diretor Silva' : 'Gabriel Mendes',
      cargo: novoRole === 'diretor' ? 'Administração Escolar' : '2º Ano - Ensino Médio',
    };
    this.atualizarSessao(usuarioAtualizado);
  }

  public logout(): void {
    localStorage.removeItem('usuario_nexo');
    this.usuarioLogado.set(null);
  }

  /** Helper privado para evitar repetição de código (DRY) */
  private atualizarSessao(usuario: Usuario): void {
    this.usuarioLogado.set(usuario);
    localStorage.setItem('usuario_nexo', JSON.stringify(usuario));
  }
}
