import { Injectable, signal } from '@angular/core';

// Definindo a estrutura de um usuário
export interface Usuario {
    nome: string;
    cargo: string;
    foto: string;
    role?: 'aluno' | 'diretor'; // Adicionando um campo opcional para o papel do usuário
}

@Injectable({
    providedIn: 'root',
})
export class AuthService {
    // O AuthService esta mais completo e melhor estruturado que o UserService, podendo ser removido futuramente.
    
    // O estado começa como 'null' (ninguém logado)
    public usuarioLogado = signal<Usuario | null>(null);

    /** Método para simular o login no frontend */
    public login(username: string): void {
        const loginLimpo = username.trim().toLowerCase();

        //Simula um Login simples: O diretor é o email e admin é a senha.
        if (loginLimpo === 'diretor' || loginLimpo === 'admin') {
            this.usuarioLogado.set({
                nome: 'Diretor Silva',
                cargo: 'Administração Escolar',
                foto: 'assets/imagensProjeto/gabrielZapelini.png', // Foto genérica para o diretor
                role: 'diretor'
            });
            // Futuramente,sera implementado o sistema de autenticação real, onde o backend retornará os dados do usuário, incluindo seu papel (aluno, diretor, etc).
            // Para logar com o Aluno,basta usar qualquer outro nome de usuário que não seja 'diretor' ou 'admin', e ele será logado como aluno.
        } else {
            this.usuarioLogado.set({
                nome: 'Gabriel Mendes',
                cargo: '2º Ano - Ensino Médio',
                foto: 'assets/imagensProjeto/gabrielZapelini.png',
                role: 'aluno'
            });
        }
    }
     public alternarPerfil(): void {
        const usuario = this.usuarioLogado();
        if (!usuario) return;

        const novoRole = usuario.role === 'aluno' ? 'diretor' : 'aluno';

        this.usuarioLogado.set({
            ...usuario,
            role: novoRole,
            nome: novoRole === 'diretor' ? 'Diretor Silva' : 'Gabriel Mendes',
            cargo: novoRole === 'diretor' ? 'Administração Escolar' : '2º Ano - Ensino Médio',
        });
    }

    //Método para deslogar
    public logout(): void {
        this.usuarioLogado.set(null);
    }
}