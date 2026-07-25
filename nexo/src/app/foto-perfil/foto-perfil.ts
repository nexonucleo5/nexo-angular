import { Component, ElementRef, OnDestroy, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UsuariosService } from '../api/usuarios.service';
import { AVATAR_PADRAO, resolverFoto } from '../core/avatar';

/** Lado da imagem final enviada ao servidor; quadrada, o bastante para telas retina. */
const LADO_FINAL = 320;
const QUALIDADE_JPEG = 0.85;

/**
 * Escolha da foto de perfil: tirar na hora pela câmera ou pegar da galeria.
 *
 * A imagem é recortada em quadrado e reduzida aqui no navegador antes de subir —
 * o servidor recebe ~30 KB em vez do arquivo original de vários MB.
 */
@Component({
  selector: 'app-foto-perfil',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './foto-perfil.html',
  styleUrl: './foto-perfil.scss',
})
export class FotoPerfil implements OnDestroy {
  private readonly usuarios = inject(UsuariosService);

  /** Foto atual do usuário (valor cru vindo da sessão). */
  readonly foto = input<string | null>(null);
  /** Emitido quando o servidor confirma a troca ou a remoção. */
  readonly alterada = output<void>();

  private readonly arquivoInput = viewChild<ElementRef<HTMLInputElement>>('arquivoInput');
  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  readonly camera = signal(false);
  readonly enviando = signal(false);
  readonly erro = signal('');
  /** Prévia local (data URI) mostrada enquanto o envio acontece. */
  readonly previa = signal<string | null>(null);

  readonly imagemExibida = computed(() => this.previa() ?? resolverFoto(this.foto()));
  /** Só oferece "remover" quando existe foto própria — não quando é o avatar padrão. */
  readonly temFoto = computed(() => this.imagemExibida() !== AVATAR_PADRAO);

  private stream: MediaStream | null = null;

  ngOnDestroy(): void {
    this.pararCamera();
  }

  // ── Galeria ────────────────────────────────────────────────────────────

  abrirGaleria(): void {
    this.erro.set('');
    this.arquivoInput()?.nativeElement.click();
  }

  async aoEscolherArquivo(evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = ''; // permite escolher o mesmo arquivo de novo
    if (!arquivo) return;

    if (!arquivo.type.startsWith('image/')) {
      this.erro.set('Escolha um arquivo de imagem.');
      return;
    }
    try {
      const bitmap = await createImageBitmap(arquivo);
      await this.enviarDoBitmap(bitmap);
    } catch {
      this.erro.set('Não foi possível ler essa imagem.');
    }
  }

  // ── Câmera ─────────────────────────────────────────────────────────────

  async abrirCamera(): Promise<void> {
    this.erro.set('');
    if (!navigator.mediaDevices?.getUserMedia) {
      this.erro.set('Este dispositivo não permite acesso à câmera pelo navegador.');
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 720 }, height: { ideal: 720 } },
        audio: false,
      });
      this.camera.set(true);
      // o <video> só existe depois que o bloco da câmera é renderizado
      setTimeout(() => {
        const el = this.video()?.nativeElement;
        if (el && this.stream) {
          el.srcObject = this.stream;
          el.play().catch(() => undefined);
        }
      });
    } catch {
      this.erro.set('Não foi possível acessar a câmera. Verifique a permissão do navegador.');
      this.pararCamera();
    }
  }

  async capturar(): Promise<void> {
    const el = this.video()?.nativeElement;
    if (!el || !el.videoWidth) return;
    const bitmap = await createImageBitmap(el);
    this.pararCamera();
    await this.enviarDoBitmap(bitmap);
  }

  fecharCamera(): void {
    this.pararCamera();
  }

  private pararCamera(): void {
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.camera.set(false);
  }

  // ── Processamento e envio ──────────────────────────────────────────────

  /** Recorta o centro em quadrado, reduz para LADO_FINAL e envia como JPEG. */
  private async enviarDoBitmap(bitmap: ImageBitmap): Promise<void> {
    const lado = Math.min(bitmap.width, bitmap.height);
    const sx = (bitmap.width - lado) / 2;
    const sy = (bitmap.height - lado) / 2;

    const canvas = document.createElement('canvas');
    canvas.width = LADO_FINAL;
    canvas.height = LADO_FINAL;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      this.erro.set('Não foi possível processar a imagem.');
      return;
    }
    ctx.drawImage(bitmap, sx, sy, lado, lado, 0, 0, LADO_FINAL, LADO_FINAL);
    bitmap.close();

    this.previa.set(canvas.toDataURL('image/jpeg', QUALIDADE_JPEG));

    const blob: Blob | null = await new Promise((r) => canvas.toBlob(r, 'image/jpeg', QUALIDADE_JPEG));
    if (!blob) {
      this.erro.set('Não foi possível processar a imagem.');
      this.previa.set(null);
      return;
    }

    this.enviando.set(true);
    this.usuarios.enviarFoto(blob).subscribe({
      next: () => {
        this.enviando.set(false);
        this.previa.set(null); // a partir daqui vale a foto que veio do servidor
        this.alterada.emit();
      },
      error: (e) => {
        this.enviando.set(false);
        this.previa.set(null);
        this.erro.set(e?.error?.message ?? 'Falha ao enviar a foto.');
      },
    });
  }

  remover(): void {
    if (this.enviando()) return;
    this.enviando.set(true);
    this.erro.set('');
    this.usuarios.removerFoto().subscribe({
      next: () => {
        this.enviando.set(false);
        this.previa.set(null);
        this.alterada.emit();
      },
      error: () => {
        this.enviando.set(false);
        this.erro.set('Falha ao remover a foto.');
      },
    });
  }

  protected readonly avatarPadrao = AVATAR_PADRAO;
}
