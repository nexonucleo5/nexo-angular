import { Component, ElementRef, HostListener, input, output, signal, viewChild, inject, } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-menu-usuario',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './menu-usuario.html',
  styleUrl: './menu-usuario.scss',
})
export class MenuUsuario {
  authService = inject(AuthService);
  router = inject(Router);
  userName = input.required<string>();
  userClass = input<string>('Estudante');
  userPhoto = input<string>('assets/imagensProjeto/defaultUser.png');

  onLogout = output<void>();

  /** Tema atual e pedido de troca: quem persiste é o App (serviço do perfil → aplicarTema). */
  temaEscuro = input(false);
  alternarTema = output<void>();

  menuOpen = signal(false);
  menuRef = viewChild<ElementRef>('menuRef');

  toggleMenu() {
    this.menuOpen.update(v => !v);
  }

  escolherTema(escuro: boolean) {
    if (escuro !== this.temaEscuro()) this.alternarTema.emit();
  }

  @HostListener('document:click', ['$event'])
  onClickOutside(event: Event) {
    if (!this.menuRef()?.nativeElement.contains(event.target)) {
      this.menuOpen.set(false);
    }
  }

  // Método para deslogar o usuário, fechar o menu e redirecionar para a tela de login
  public signOut(): void {
    this.menuOpen.set(false);
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}