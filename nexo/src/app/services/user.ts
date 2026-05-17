import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  // Inicializa o estado padrão como 'aluno'
  private perfilUsuarioSubject = new BehaviorSubject<string>('aluno');
  
  // Observable que o Wrapper vai escutar (dar subscribe)
  perfilUsuario$ = this.perfilUsuarioSubject.asObservable();

  constructor() {}

  // Função que o seu botão de alternar perfil vai chamar
  alternarPerfil(novoPerfil: string): void {
    this.perfilUsuarioSubject.next(novoPerfil);
  }

  // Função auxiliar para obter o valor estático atual se necessário
  obterPerfilAtual(): string {
    return this.perfilUsuarioSubject.value;
  }
}