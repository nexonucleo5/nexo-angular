import { Component, ElementRef, HostListener, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-menu-usuario',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './menu-usuario.html',
  styleUrl: './menu-usuario.scss',
})
export class MenuUsuario {
  [x: string]: any;
  userName = input.required<string>();
  userClass = input<string>('Estudante');
  userPhoto = input<string>('assets/imagensProjeto/defaultUser.png');

  onLogout = output<void>();

  menuOpen = signal(false);
  menuRef = viewChild<ElementRef>('menuRef');

  toggleMenu() {
    this.menuOpen.update(v => !v);
  }

  @HostListener('document:click', ['$event'])
  onClickOutside(event: Event) {
    if (!this.menuRef()?.nativeElement.contains(event.target)) {
      this.menuOpen.set(false);
    }
  }

  public signOut(): void {
    this.menuOpen.set(false);
    this['authService'].logout();
    this['router'].navigate(['/login']);
  }
}